package com.example.data

import android.util.Log
import com.example.model.PandalItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseRepository {
    private val tag = "FirebaseRepository"

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "Firebase Auth unavailable: ${e.message}")
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "Firebase Firestore unavailable: ${e.message}")
            null
        }
    }

    // In-memory fallback if Firestore is unavailable
    private val localPandals = MutableStateFlow<List<PandalItem>>(getInitialSeedPandals())
    private var currentUserId: String? = null

    init {
        initAuth()
    }

    private fun initAuth() {
        try {
            val currentAuth = auth ?: return
            if (currentAuth.currentUser == null) {
                currentAuth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        currentUserId = result.user?.uid
                        Log.d(tag, "Signed in anonymously: $currentUserId")
                    }
                    .addOnFailureListener { e ->
                        Log.w(tag, "Anonymous auth failed, using local offline session: ${e.message}")
                        currentUserId = "local_user_${UUID.randomUUID().toString().take(6)}"
                    }
            } else {
                currentUserId = currentAuth.currentUser?.uid
            }
        } catch (e: Exception) {
            Log.w(tag, "Auth initialization error: ${e.message}")
            currentUserId = "offline_user"
        }
    }

    fun getPandalsFlow(): Flow<List<PandalItem>> = callbackFlow {
        val fs = firestore
        val currentAuth = auth
        val uid = currentAuth?.currentUser?.uid ?: currentUserId

        if (fs == null || uid == null) {
            // Fallback to local memory flow
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).run {
                localPandals.collect {
                    trySend(it)
                }
            }
            awaitClose { }
            return@callbackFlow
        }

        val collectionRef = fs.collection("users").document(uid).collection("pandals")
        var registration: ListenerRegistration? = null

        try {
            registration = collectionRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Firestore listen error: ${error.message}. Emitting local items.")
                    trySend(localPandals.value)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        try {
                            PandalItem(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                address = doc.getString("address") ?: "",
                                latitude = doc.getDouble("latitude") ?: 0.0,
                                longitude = doc.getDouble("longitude") ?: 0.0,
                                status = doc.getString("status") ?: PandalItem.STATUS_UNVISITED,
                                notes = doc.getString("notes") ?: "",
                                description = doc.getString("description") ?: "",
                                category = doc.getString("category") ?: "Pandal",
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    localPandals.value = items
                    trySend(items)
                } else if (snapshot != null && snapshot.isEmpty) {
                    // Seed initial list into Firestore for this new user
                    seedPandalsToFirestore(uid)
                    trySend(localPandals.value)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to attach snapshot listener: ${e.message}")
            trySend(localPandals.value)
        }

        awaitClose {
            registration?.remove()
        }
    }

    private fun seedPandalsToFirestore(uid: String) {
        val fs = firestore ?: return
        val batch = fs.batch()
        val collection = fs.collection("users").document(uid).collection("pandals")
        val seeds = getInitialSeedPandals()

        for (item in seeds) {
            val docRef = collection.document(item.id)
            val data = hashMapOf(
                "name" to item.name,
                "address" to item.address,
                "latitude" to item.latitude,
                "longitude" to item.longitude,
                "status" to item.status,
                "notes" to item.notes,
                "description" to item.description,
                "category" to item.category,
                "timestamp" to item.timestamp
            )
            batch.set(docRef, data)
        }
        batch.commit().addOnSuccessListener {
            Log.d(tag, "Seeded ${seeds.size} pandals into Firestore")
        }.addOnFailureListener { e ->
            Log.w(tag, "Seeding Firestore failed: ${e.message}")
        }
    }

    suspend fun addPandal(pandal: PandalItem): Result<Unit> {
        val id = if (pandal.id.isBlank()) UUID.randomUUID().toString() else pandal.id
        val itemWithId = pandal.copy(id = id)

        // Always update local state first for immediate UI responsiveness
        val currentList = localPandals.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == id || (it.name.equals(pandal.name, ignoreCase = true) && it.address.equals(pandal.address, ignoreCase = true)) }
        if (existingIndex >= 0) {
            currentList[existingIndex] = itemWithId
        } else {
            currentList.add(0, itemWithId)
        }
        localPandals.value = currentList

        // Sync with Firestore if available
        return try {
            val fs = firestore
            val uid = auth?.currentUser?.uid ?: currentUserId
            if (fs != null && uid != null) {
                val data = hashMapOf(
                    "name" to itemWithId.name,
                    "address" to itemWithId.address,
                    "latitude" to itemWithId.latitude,
                    "longitude" to itemWithId.longitude,
                    "status" to itemWithId.status,
                    "notes" to itemWithId.notes,
                    "description" to itemWithId.description,
                    "category" to itemWithId.category,
                    "timestamp" to itemWithId.timestamp
                )
                fs.collection("users").document(uid).collection("pandals")
                    .document(id)
                    .set(data, SetOptions.merge())
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(tag, "Add pandal Firestore sync failed: ${e.message}")
            Result.success(Unit) // Still successful locally
        }
    }

    suspend fun updatePandalStatus(id: String, status: String): Result<Unit> {
        // Update local state immediately
        localPandals.value = localPandals.value.map {
            if (it.id == id) it.copy(status = status) else it
        }

        return try {
            val fs = firestore
            val uid = auth?.currentUser?.uid ?: currentUserId
            if (fs != null && uid != null) {
                fs.collection("users").document(uid).collection("pandals")
                    .document(id)
                    .update("status", status)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(tag, "Update status Firestore error: ${e.message}")
            Result.success(Unit)
        }
    }

    suspend fun updatePandalNotes(id: String, notes: String): Result<Unit> {
        localPandals.value = localPandals.value.map {
            if (it.id == id) it.copy(notes = notes) else it
        }

        return try {
            val fs = firestore
            val uid = auth?.currentUser?.uid ?: currentUserId
            if (fs != null && uid != null) {
                fs.collection("users").document(uid).collection("pandals")
                    .document(id)
                    .update("notes", notes)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(tag, "Update notes Firestore error: ${e.message}")
            Result.success(Unit)
        }
    }

    suspend fun deletePandal(id: String): Result<Unit> {
        localPandals.value = localPandals.value.filterNot { it.id == id }

        return try {
            val fs = firestore
            val uid = auth?.currentUser?.uid ?: currentUserId
            if (fs != null && uid != null) {
                fs.collection("users").document(uid).collection("pandals")
                    .document(id)
                    .delete()
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(tag, "Delete pandal Firestore error: ${e.message}")
            Result.success(Unit)
        }
    }

    private fun getInitialSeedPandals(): List<PandalItem> {
        return listOf(
            PandalItem(
                id = "pandal_lalbaug",
                name = "Lalbaugcha Raja",
                address = "Lalbaug Market, GD Ambekar Marg, Mumbai",
                latitude = 18.9912,
                longitude = 72.8358,
                status = PandalItem.STATUS_UNVISITED,
                notes = "Navas line takes 4-8 hours. Mukh Darshan line is faster (~1.5 hours). Best time early morning 4 AM.",
                description = "The King of Lalbaug, Mumbai's most revered and iconic sarvajanik pandal founded in 1934.",
                category = "Iconic Mega Pandal"
            ),
            PandalItem(
                id = "pandal_mumbaicha_raja",
                name = "Mumbaicha Raja (Ganesh Galli)",
                address = "1st Lane, Ganesh Galli, Lalbaug, Mumbai",
                latitude = 18.9944,
                longitude = 72.8365,
                status = PandalItem.STATUS_UNVISITED,
                notes = "Famous for breathtaking 22ft idol and grand temple architecture replicas. Right around the corner from Lalbaugcha Raja.",
                description = "Oldest pandal in Central Mumbai, celebrating grand cultural themes since 1928.",
                category = "Heritage Pandal"
            ),
            PandalItem(
                id = "pandal_chintamani",
                name = "Chintamani Chinchpokli",
                address = "Dattaram Lad Marg, Chinchpokli, Mumbai",
                latitude = 18.9880,
                longitude = 72.8327,
                status = PandalItem.STATUS_VISITED,
                notes = "Celebrated 100+ years of glory. Majestic seated posture with exquisite jewelry and calm aura.",
                description = "One of Mumbai's most beloved centennial pandals with profound artistic heritage.",
                category = "Centennial Pandal"
            ),
            PandalItem(
                id = "pandal_gsb_kings_circle",
                name = "GSB Seva Mandal King's Circle",
                address = "Guru Tegh Bahadur Nagar, King's Circle, Matunga, Mumbai",
                latitude = 19.0305,
                longitude = 72.8596,
                status = PandalItem.STATUS_UNVISITED,
                notes = "Richest Ganpati idol in the world, adorned with over 60kg gold and 300kg silver. Traditional Clay idol with vedic rituals.",
                description = "World famous eco-friendly clay deity with traditional South Indian poojas and Mahaprasad.",
                category = "Vedic Gold Pandal"
            ),
            PandalItem(
                id = "pandal_khetwadi",
                name = "Khetwadi 12th Lane (Khetwadicha Ganraj)",
                address = "12th Lane, Khetwadi, Girgaon, Mumbai",
                latitude = 18.9592,
                longitude = 72.8210,
                status = PandalItem.STATUS_UNVISITED,
                notes = "Renowned for record-breaking tallest idols (up to 45 feet) and cinema-grade lighting effects.",
                description = "Multiple award-winning spectacle featuring India's most towering artistic murtis.",
                category = "Tallest Murti Pandal"
            ),
            PandalItem(
                id = "pandal_andheri_raja",
                name = "Andheri Cha Raja",
                address = "Veera Desai Road, Azad Nagar, Andheri West, Mumbai",
                latitude = 19.1235,
                longitude = 72.8384,
                status = PandalItem.STATUS_UNVISITED,
                notes = "Known as Lalbaugcha Raja of Western Suburbs. Stays for 16 days till Sankashti Chaturthi.",
                description = "Premier western suburban pandal renowned for wish fulfillment and lavish set designs.",
                category = "Suburban King Pandal"
            )
        )
    }
}
