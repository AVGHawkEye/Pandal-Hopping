package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.model.PandalItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min

class GeminiSearchHandler {
    private val tag = "GeminiSearchHandler"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Instant Zero-Latency Fuzzy & Substring Match against user's saved Firestore checklist.
     * Matches substrings, variations, token fragments (e.g. 'mum', 'chintamani', 'king', 'lal').
     */
    fun matchInAppChecklist(query: String, savedPandals: List<PandalItem>): List<PandalItem> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isBlank()) return emptyList()

        return savedPandals.filter { pandal ->
            val nameLower = pandal.name.lowercase()
            val addrLower = pandal.address.lowercase()
            val notesLower = pandal.notes.lowercase()
            val catLower = pandal.category.lowercase()

            // 1. Direct Substring Match anywhere
            if (nameLower.contains(cleanQuery) || addrLower.contains(cleanQuery) ||
                notesLower.contains(cleanQuery) || catLower.contains(cleanQuery)
            ) {
                return@filter true
            }

            // 2. Tokenized match (e.g. user typed "mumbai raja")
            val tokens = cleanQuery.split(" ").filter { it.isNotBlank() }
            if (tokens.isNotEmpty() && tokens.all { token ->
                    nameLower.contains(token) || addrLower.contains(token)
                }) {
                return@filter true
            }

            // 3. Typo-tolerant fuzzy match on words
            val nameWords = nameLower.split(" ", "(", ")", "-", ",").filter { it.length >= 3 }
            for (word in nameWords) {
                if (word.startsWith(cleanQuery) || cleanQuery.startsWith(word)) {
                    return@filter true
                }
                // Allow 1-2 edit distance for short/medium queries
                if (cleanQuery.length >= 3 && word.length >= 3) {
                    val distance = levenshteinDistance(cleanQuery, word)
                    val maxAllowed = if (cleanQuery.length <= 4) 1 else 2
                    if (distance <= maxAllowed) {
                        return@filter true
                    }
                }
            }

            // 4. Common Indian festival keyword aliases
            val matchedAlias = when {
                cleanQuery.startsWith("lal") || cleanQuery.contains("raja") ->
                    nameLower.contains("lalbaug") || nameLower.contains("raja")
                cleanQuery.startsWith("mum") || cleanQuery.contains("galli") ->
                    nameLower.contains("mumbai") || nameLower.contains("galli")
                cleanQuery.startsWith("chin") || cleanQuery.contains("chinta") ->
                    nameLower.contains("chintamani") || nameLower.contains("chinchpokli")
                cleanQuery.startsWith("king") || cleanQuery.contains("gsb") ->
                    nameLower.contains("gsb") || nameLower.contains("king")
                cleanQuery.startsWith("khet") || cleanQuery.contains("girgaon") ->
                    nameLower.contains("khetwadi") || addrLower.contains("girgaon")
                cleanQuery.startsWith("andh") ->
                    nameLower.contains("andheri")
                else -> false
            }

            matchedAlias
        }
    }

    /**
     * Power the top search bar using Gemini Intelligence with Google Search/Places Grounding
     * to find any real-world pandal or landmark in real-time.
     */
    suspend fun searchRealWorldPlaces(query: String): List<PandalItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(tag, "Using high-fidelity offline places directory (GEMINI_API_KEY is placeholder or empty)")
            return@withContext searchOfflinePlacesDirectory(cleanQuery)
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val prompt = """
                You are a Google Places and Pandal Grounding engine.
                Find real-world festival pandals, mandals, temples, or landmark locations matching: "$cleanQuery".
                Return ONLY a JSON array of up to 5 matching real-world places with exact or highly accurate coordinates in India (e.g. Mumbai, Pune, Kolkata, etc.).
                Format:
                [
                  {
                    "name": "Full Pandal/Place Name",
                    "address": "Street, Neighborhood, City",
                    "latitude": 18.9912,
                    "longitude": 72.8358,
                    "description": "Brief 1-sentence cultural or landmark significance",
                    "category": "Sarvajanik Pandal / Temple / Landmark"
                  }
                ]
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                // Enable Google Search Grounding
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("googleSearch", JSONObject())
                    })
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(tag, "Gemini API error: ${response.code} ${response.message}")
                return@withContext searchOfflinePlacesDirectory(cleanQuery)
            }

            val responseBody = response.body?.string() ?: ""
            val parsedList = parseGeminiPlacesResponse(responseBody)
            if (parsedList.isNotEmpty()) {
                return@withContext parsedList
            } else {
                return@withContext searchOfflinePlacesDirectory(cleanQuery)
            }
        } catch (e: Exception) {
            Log.w(tag, "Gemini search failed: ${e.message}. Falling back to directory.")
            return@withContext searchOfflinePlacesDirectory(cleanQuery)
        }
    }

    private fun parseGeminiPlacesResponse(jsonString: String): List<PandalItem> {
        val results = mutableListOf<PandalItem>()
        try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return results
            if (candidates.length() == 0) return results
            val firstCand = candidates.getJSONObject(0)
            val content = firstCand.optJSONObject("content") ?: return results
            val parts = content.optJSONArray("parts") ?: return results
            var text = ""
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    text += part.getString("text")
                }
            }

            // Extract JSON array from Markdown code fence if wrapped
            val jsonStart = text.indexOf('[')
            val jsonEnd = text.lastIndexOf(']')
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                val jsonArrayStr = text.substring(jsonStart, jsonEnd + 1)
                val array = JSONArray(jsonArrayStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    results.add(
                        PandalItem(
                            id = "grounded_${UUID.randomUUID().toString().take(8)}",
                            name = obj.optString("name", "Unknown Pandal"),
                            address = obj.optString("address", "Mumbai, Maharashtra"),
                            latitude = obj.optDouble("latitude", 18.9912),
                            longitude = obj.optDouble("longitude", 72.8358),
                            status = PandalItem.STATUS_UNVISITED,
                            description = obj.optString("description", ""),
                            category = obj.optString("category", "Grounded Place")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse Gemini grounding JSON: ${e.message}")
        }
        return results
    }

    /**
     * Rich curated directory of real-world Pandals and Landmarks across Mumbai, Pune & Kolkata
     * to guarantee robust real-time responses regardless of external API key availability.
     */
    private fun searchOfflinePlacesDirectory(query: String): List<PandalItem> {
        val q = query.lowercase().trim()
        val allDirectory = listOf(
            PandalItem(
                id = "dir_lalbaug",
                name = "Lalbaugcha Raja",
                address = "GD Ambekar Marg, Lalbaug, Parel, Mumbai",
                latitude = 18.9912,
                longitude = 72.8358,
                description = "Legendary wish-fulfilling idol drawing millions during Ganeshotsav.",
                category = "Iconic Mega Pandal"
            ),
            PandalItem(
                id = "dir_mumbaicha_raja",
                name = "Mumbaicha Raja (Ganesh Galli)",
                address = "1st Ganesh Galli, Lalbaug, Mumbai",
                latitude = 18.9944,
                longitude = 72.8365,
                description = "Grand thematic architecture celebrating monumental Indian temple replicas.",
                category = "Heritage Pandal"
            ),
            PandalItem(
                id = "dir_chintamani",
                name = "Chinchpokli Cha Chintamani",
                address = "Dattaram Lad Marg, Chinchpokli, Mumbai",
                latitude = 18.9880,
                longitude = 72.8327,
                description = "Over a century old, celebrated for divine aesthetics and serenity.",
                category = "Centennial Pandal"
            ),
            PandalItem(
                id = "dir_tejukaya",
                name = "Tejukaya Mansion Ganpati",
                address = "Tejukaya Compound, Lalbaug, Mumbai",
                latitude = 18.9928,
                longitude = 72.8349,
                description = "Intricately hand-crafted eco-friendly idol with vibrant youth mandal.",
                category = "Eco-Friendly Pandal"
            ),
            PandalItem(
                id = "dir_gsb_kings",
                name = "GSB Seva Mandal King's Circle",
                address = "Bhakti Vedant Marg, King's Circle, Matunga, Mumbai",
                latitude = 19.0305,
                longitude = 72.8596,
                description = "The Golden Ganesha adorned in genuine gold and silver ornamentations.",
                category = "Vedic Gold Pandal"
            ),
            PandalItem(
                id = "dir_gsb_wadala",
                name = "GSB Sarvajanik Ganeshotsav Wadala",
                address = "Ram Mandir, Katrak Road, Wadala, Mumbai",
                latitude = 19.0195,
                longitude = 72.8570,
                description = "Revered community mandal offering traditional Konkani poojas.",
                category = "Community Pandal"
            ),
            PandalItem(
                id = "dir_khetwadi",
                name = "Khetwadi 12th Lane (Khetwadicha Ganraj)",
                address = "12th Lane, Khetwadi, Girgaon, Mumbai",
                latitude = 18.9592,
                longitude = 72.8210,
                description = "Renowned for gigantic idols up to 45 feet and royal court sets.",
                category = "Tallest Murti Pandal"
            ),
            PandalItem(
                id = "dir_andheri_raja",
                name = "Andheri Cha Raja (Azad Nagar)",
                address = "Veera Desai Road, Azad Nagar, Andheri West, Mumbai",
                latitude = 19.1235,
                longitude = 72.8384,
                description = "Celebrity-frequented western suburbs pandal with 16-day darshan.",
                category = "Suburban King Pandal"
            ),
            PandalItem(
                id = "dir_sahyadri_chembur",
                name = "Sahyadri Krida Mandal Chembur",
                address = "Tilak Nagar, Chembur, Mumbai",
                latitude = 19.0682,
                longitude = 72.8988,
                description = "Famous for breathtaking full-scale architectural replicas of world monuments.",
                category = "Thematic Pandal"
            ),
            PandalItem(
                id = "dir_keshavji_naik",
                name = "Keshavji Naik Chawl Sarvajanik Ganeshotsav",
                address = "Khadilkar Road, Girgaon, Mumbai",
                latitude = 18.9554,
                longitude = 72.8188,
                description = "Mumbai's first ever sarvajanik pandal started by Lokmanya Tilak in 1893.",
                category = "Historic First Pandal (1893)"
            ),
            PandalItem(
                id = "dir_siddhivinayak",
                name = "Shree Siddhivinayak Ganapati Temple",
                address = "SK Bole Marg, Prabhadevi, Mumbai",
                latitude = 19.0169,
                longitude = 72.8304,
                description = "Historic and world-renowned temple of Lord Ganesha.",
                category = "Historic Temple"
            ),
            PandalItem(
                id = "dir_dagdusheth",
                name = "Shreemant Dagdusheth Halwai Ganpati",
                address = "Budhwar Peth, Pune, Maharashtra",
                latitude = 18.5165,
                longitude = 73.8562,
                description = "One of India's richest and most famous heritage Ganesha mandals.",
                category = "Pune Landmark Pandal"
            ),
            PandalItem(
                id = "dir_bagbazar",
                name = "Bagbazar Sarbojanin Durgotsav",
                address = "Bagbazar Ghat, North Kolkata, West Bengal",
                latitude = 22.6025,
                longitude = 88.3685,
                description = "Centenary Durga Puja pandal famous for traditional Sabeki Pratima.",
                category = "Kolkata Centennial Pandal"
            ),
            PandalItem(
                id = "dir_college_sq",
                name = "College Square Durga Puja",
                address = "College Square, Central Kolkata, West Bengal",
                latitude = 22.5746,
                longitude = 88.3639,
                description = "Spectacular lake reflections and grand illumination displays.",
                category = "Kolkata Waterfront Pandal"
            )
        )

        return allDirectory.filter { item ->
            item.name.lowercase().contains(q) ||
            item.address.lowercase().contains(q) ||
            item.category.lowercase().contains(q) ||
            item.description.lowercase().contains(q) ||
            q.split(" ").any { token -> item.name.lowercase().contains(token) }
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
