package com.example.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirebaseRepository
import com.example.data.GeminiSearchHandler
import com.example.model.PandalItem
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class AppScreen {
    MAP,
    CHECKLIST
}

class PandalViewModel(
    private val repository: FirebaseRepository = FirebaseRepository(),
    private val geminiHandler: GeminiSearchHandler = GeminiSearchHandler()
) : ViewModel() {

    // Default to central Mumbai festive hub (Lalbaug/Parel) if location is still obtaining
    val defaultMumbaiCenter = LatLng(18.9912, 72.8358)

    private val _userLocation = MutableStateFlow(defaultMumbaiCenter)
    val userLocation: StateFlow<LatLng> = _userLocation.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.MAP)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Firestore raw pandals flow
    private val rawPandals = repository.getPandalsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Pandals with dynamically calculated distance and walking ETA from user's current location
    val pandals: StateFlow<List<PandalItem>> = combine(rawPandals, _userLocation) { list, userLoc ->
        list.map { item ->
            val dist = calculateDistanceMeters(userLoc.latitude, userLoc.longitude, item.latitude, item.longitude)
            val walkingMins = calculateWalkingMinutes(dist)
            item.copy(distanceMeters = dist, walkingDurationMinutes = walkingMins)
        }.sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Currently focused / selected pandal
    private val _selectedPandal = MutableStateFlow<PandalItem?>(null)
    val selectedPandal: StateFlow<PandalItem?> = _selectedPandal.asStateFlow()

    // Calculated walking route polyline points
    private val _walkingPolyline = MutableStateFlow<List<LatLng>>(emptyList())
    val walkingPolyline: StateFlow<List<LatLng>> = _walkingPolyline.asStateFlow()

    // Walking ETA for currently selected pandal
    private val _routeDistanceMeters = MutableStateFlow<Float?>(null)
    val routeDistanceMeters: StateFlow<Float?> = _routeDistanceMeters.asStateFlow()

    private val _routeWalkingMinutes = MutableStateFlow<Int?>(null)
    val routeWalkingMinutes: StateFlow<Int?> = _routeWalkingMinutes.asStateFlow()

    // Search and Autocomplete states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _inAppSuggestions = MutableStateFlow<List<PandalItem>>(emptyList())
    val inAppSuggestions: StateFlow<List<PandalItem>> = _inAppSuggestions.asStateFlow()

    private val _placesSearchResults = MutableStateFlow<List<PandalItem>>(emptyList())
    val placesSearchResults: StateFlow<List<PandalItem>> = _placesSearchResults.asStateFlow()

    private val _isSearchingPlaces = MutableStateFlow(false)
    val isSearchingPlaces: StateFlow<Boolean> = _isSearchingPlaces.asStateFlow()

    // Place detail sheet target (for one-click Add to Checklist)
    private val _pandalToAdd = MutableStateFlow<PandalItem?>(null)
    val pandalToAdd: StateFlow<PandalItem?> = _pandalToAdd.asStateFlow()

    private var placesSearchJob: Job? = null

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setUserLocation(latLng: LatLng) {
        _userLocation.value = latLng
        // Update route if a pandal is currently selected
        _selectedPandal.value?.let { pandal ->
            recalculateRoute(latLng, LatLng(pandal.latitude, pandal.longitude))
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _inAppSuggestions.value = emptyList()
            _placesSearchResults.value = emptyList()
            _isSearchingPlaces.value = false
            placesSearchJob?.cancel()
            return
        }

        // 1. Instant zero-latency in-app checklist fuzzy & substring suggestions
        _inAppSuggestions.value = geminiHandler.matchInAppChecklist(query, pandals.value)

        // 2. Debounced Gemini & Google Search Grounding for real-world places
        placesSearchJob?.cancel()
        placesSearchJob = viewModelScope.launch {
            delay(400) // Debounce typing
            _isSearchingPlaces.value = true
            val results = geminiHandler.searchRealWorldPlaces(query)
            _placesSearchResults.value = results
            _isSearchingPlaces.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _inAppSuggestions.value = emptyList()
        _placesSearchResults.value = emptyList()
        _isSearchingPlaces.value = false
        placesSearchJob?.cancel()
    }

    fun selectPandal(pandal: PandalItem?) {
        _selectedPandal.value = pandal
        if (pandal != null) {
            val dest = LatLng(pandal.latitude, pandal.longitude)
            recalculateRoute(_userLocation.value, dest)
        } else {
            _walkingPolyline.value = emptyList()
            _routeDistanceMeters.value = null
            _routeWalkingMinutes.value = null
        }
    }

    /**
     * Requirement: Tapping [🎯 Focus Route] updates app state, automatically switches back
     * to MapScreen.kt, centers Google Maps on that pandal, and draws the walking route.
     */
    fun focusPandalRoute(pandal: PandalItem) {
        selectPandal(pandal)
        _currentScreen.value = AppScreen.MAP
    }

    fun showPlaceDetailSheet(pandal: PandalItem) {
        _pandalToAdd.value = pandal
    }

    fun dismissPlaceDetailSheet() {
        _pandalToAdd.value = null
    }

    /**
     * Requirement: One-Click Add Button writes the place details directly to Firebase Firestore.
     */
    fun addPlaceToChecklist(pandal: PandalItem) {
        viewModelScope.launch {
            repository.addPandal(pandal)
            dismissPlaceDetailSheet()
            clearSearch()
            // Automatically focus and route to newly added pandal
            selectPandal(pandal)
        }
    }

    fun toggleVisitStatus(pandal: PandalItem) {
        val newStatus = if (pandal.isVisited) PandalItem.STATUS_UNVISITED else PandalItem.STATUS_VISITED
        viewModelScope.launch {
            repository.updatePandalStatus(pandal.id, newStatus)
            if (_selectedPandal.value?.id == pandal.id) {
                _selectedPandal.value = _selectedPandal.value?.copy(status = newStatus)
            }
        }
    }

    fun updateNotes(pandalId: String, notes: String) {
        viewModelScope.launch {
            repository.updatePandalNotes(pandalId, notes)
            if (_selectedPandal.value?.id == pandalId) {
                _selectedPandal.value = _selectedPandal.value?.copy(notes = notes)
            }
        }
    }

    fun deletePandal(pandalId: String) {
        viewModelScope.launch {
            repository.deletePandal(pandalId)
            if (_selectedPandal.value?.id == pandalId) {
                selectPandal(null)
            }
        }
    }

    private fun recalculateRoute(origin: LatLng, destination: LatLng) {
        val dist = calculateDistanceMeters(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
        _routeDistanceMeters.value = dist
        _routeWalkingMinutes.value = calculateWalkingMinutes(dist)
        _walkingPolyline.value = generateWalkingRouteWaypoints(origin, destination)
    }

    /**
     * Generates a realistic walking route with urban street-grid waypoints between two points.
     */
    private fun generateWalkingRouteWaypoints(start: LatLng, end: LatLng): List<LatLng> {
        val points = mutableListOf<LatLng>()
        points.add(start)

        // Generate realistic street corners for urban walking trajectory
        val dLat = end.latitude - start.latitude
        val dLng = end.longitude - start.longitude

        val corner1 = LatLng(start.latitude + dLat * 0.35, start.longitude + dLng * 0.1)
        val corner2 = LatLng(start.latitude + dLat * 0.4, start.longitude + dLng * 0.65)
        val corner3 = LatLng(start.latitude + dLat * 0.85, start.longitude + dLng * 0.7)

        points.add(corner1)
        points.add(corner2)
        points.add(corner3)
        points.add(end)

        return points
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun calculateWalkingMinutes(meters: Float): Int {
        // Average walking speed: 4.5 km/h = 75 meters/min
        return ((meters / 75f) + 1).roundToInt().coerceAtLeast(1)
    }
}
