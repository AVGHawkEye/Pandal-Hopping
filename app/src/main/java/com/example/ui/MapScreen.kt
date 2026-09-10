package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PandalItem
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: PandalViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pandals by viewModel.pandals.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val selectedPandal by viewModel.selectedPandal.collectAsState()
    val walkingPolyline by viewModel.walkingPolyline.collectAsState()
    val routeDistMeters by viewModel.routeDistanceMeters.collectAsState()
    val routeWalkingMins by viewModel.routeWalkingMinutes.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val inAppSuggestions by viewModel.inAppSuggestions.collectAsState()
    val placesSearchResults by viewModel.placesSearchResults.collectAsState()
    val isSearchingPlaces by viewModel.isSearchingPlaces.collectAsState()
    val pandalToAdd by viewModel.pandalToAdd.collectAsState()

    var mapType by remember { mutableStateOf(MapType.NORMAL) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation, 14.5f)
    }

    // Animate camera when selected pandal changes or focus route is called
    LaunchedEffect(selectedPandal) {
        selectedPandal?.let { pandal ->
            val pandalLatLng = LatLng(pandal.latitude, pandal.longitude)
            try {
                // Fit both user location and pandal in frame if possible
                val bounds = LatLngBounds.builder()
                    .include(userLocation)
                    .include(pandalLatLng)
                    .build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 180),
                    1000
                )
            } catch (e: Exception) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(pandalLatLng, 16f),
                    1000
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // --- Official Google Maps View ---
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("google_map_view"),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = mapType,
                isMyLocationEnabled = false // Custom blue marker used for consistent styling
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = true,
                rotationGesturesEnabled = true
            )
        ) {
            // 1. Blue Location Dot for Current User
            Marker(
                state = MarkerState(position = userLocation),
                title = "Your Location",
                snippet = "Live GPS Center",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )

            // 2. Custom Markers for Pandals (Red for UNVISITED, Green for VISITED)
            // When a pandal is isolated/focused in focus mode, highlight it
            pandals.forEach { pandal ->
                val isFocused = selectedPandal?.id == pandal.id
                val markerHue = if (pandal.isVisited) {
                    BitmapDescriptorFactory.HUE_GREEN
                } else {
                    BitmapDescriptorFactory.HUE_RED
                }

                Marker(
                    state = MarkerState(position = LatLng(pandal.latitude, pandal.longitude)),
                    title = pandal.name,
                    snippet = if (pandal.isVisited) "✅ Visited • ${pandal.address}" else "📍 Unvisited • ${pandal.address}",
                    icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                    alpha = if (selectedPandal == null || isFocused) 1.0f else 0.45f,
                    onClick = {
                        viewModel.selectPandal(pandal)
                        true
                    }
                )
            }

            // 3. Live Turn-by-Turn Polyline Walking Route
            if (walkingPolyline.isNotEmpty()) {
                Polyline(
                    points = walkingPolyline,
                    color = SaffronPrimary,
                    width = 14f
                )
            }
        }

        // --- Top Global Places & In-App Search Bar ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 44.dp)
        ) {
            PlacesSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onClear = { viewModel.clearSearch() },
                isSearching = isSearchingPlaces
            )

            // Auto-Suggestion Dropdown Popup
            AnimatedVisibility(
                visible = searchQuery.isNotBlank() && (inAppSuggestions.isNotEmpty() || placesSearchResults.isNotEmpty() || isSearchingPlaces),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(max = 380.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        // Section 1: In-App Saved Checklist Matches (Gemini smart fuzzy/substring)
                        if (inAppSuggestions.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = "SAVED IN CHECKLIST",
                                    badge = "${inAppSuggestions.size} Matches"
                                )
                            }
                            items(inAppSuggestions) { item ->
                                SuggestionItemRow(
                                    pandal = item,
                                    isSaved = true,
                                    onClick = {
                                        viewModel.selectPandal(item)
                                        viewModel.clearSearch()
                                    }
                                )
                            }
                        }

                        // Section 2: Real-World Google Places Grounding (Gemini Intelligence)
                        if (placesSearchResults.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = "GOOGLE PLACES GROUNDING",
                                    badge = "Real-World Locations"
                                )
                            }
                            items(placesSearchResults) { place ->
                                SuggestionItemRow(
                                    pandal = place,
                                    isSaved = false,
                                    onClick = {
                                        viewModel.showPlaceDetailSheet(place)
                                    }
                                )
                            }
                        }

                        if (isSearchingPlaces) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = SaffronPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Grounding places with Gemini...",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Map Action Floating Controls (Right Side) ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Re-center on User Location FAB
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(userLocation, 15f),
                            800
                        )
                    }
                },
                containerColor = DarkSurfaceVariant,
                contentColor = SaffronPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("recenter_location_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location"
                )
            }

            // Map Layer Toggle (Normal vs Hybrid)
            FloatingActionButton(
                onClick = {
                    mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
                },
                containerColor = DarkSurfaceVariant,
                contentColor = TextPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("toggle_map_type_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Map Style"
                )
            }
        }

        // --- Bottom Focus Mode & Walking Route Card ---
        AnimatedVisibility(
            visible = selectedPandal != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 90.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            selectedPandal?.let { pandal ->
                FocusModePandalCard(
                    pandal = pandal,
                    distanceMeters = routeDistMeters,
                    walkingMinutes = routeWalkingMins,
                    onDismiss = { viewModel.selectPandal(null) },
                    onToggleVisited = { viewModel.toggleVisitStatus(pandal) },
                    onHandoffGoogleMaps = {
                        openGoogleMapsWalkingDirections(context, pandal.latitude, pandal.longitude)
                    }
                )
            }
        }

        // --- Bottom Sheet: One-Click Add to Checklist (Places Grounding Result) ---
        pandalToAdd?.let { place ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissPlaceDetailSheet() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = DarkSurface
            ) {
                PlaceDetailAddSheet(
                    place = place,
                    onAddClicked = { viewModel.addPlaceToChecklist(place) },
                    onCancel = { viewModel.dismissPlaceDetailSheet() }
                )
            }
        }
    }
}

@Composable
private fun PlacesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("places_search_bar"),
        shape = RoundedCornerShape(28.dp),
        color = DarkSurface,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Gemini Search",
                tint = SaffronPrimary,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Search pandals or places (e.g. mum, lal)...",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_input_field")
            )

            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = SaffronPrimary,
                    strokeWidth = 2.dp
                )
            } else if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String, badge: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = SaffronPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = badge,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun SuggestionItemRow(
    pandal: PandalItem,
    isSaved: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isSaved) SaffronPrimary.copy(alpha = 0.15f) else EmeraldGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Default.Place else Icons.Default.Add,
                contentDescription = null,
                tint = if (isSaved) SaffronPrimary else EmeraldGreen,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pandal.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pandal.address,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSaved) {
            Text(
                text = if (pandal.isVisited) "VISITED" else "UNVISITED",
                color = if (pandal.isVisited) EmeraldGreen else CrimsonRed,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(
                        (if (pandal.isVisited) EmeraldGreen else CrimsonRed).copy(alpha = 0.12f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        } else {
            Text(
                text = "+ ADD",
                color = SaffronPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(SaffronPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun FocusModePandalCard(
    pandal: PandalItem,
    distanceMeters: Float?,
    walkingMinutes: Int?,
    onDismiss: () -> Unit,
    onToggleVisited: () -> Unit,
    onHandoffGoogleMaps: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("focus_mode_pandal_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top Row: Title, Status Badge, Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pandal.name,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pandal.address,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close focus",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Walking Route ETA Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsWalk,
                    contentDescription = "Walk ETA",
                    tint = SaffronPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val walkText = if (walkingMinutes != null && distanceMeters != null) {
                    "~$walkingMinutes min walk • ${formatDistance(distanceMeters)}"
                } else {
                    "Calculating live shortest walking path..."
                }
                Text(
                    text = walkText,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.weight(1f))

                // Status Badge
                Text(
                    text = if (pandal.isVisited) "VISITED" else "UNVISITED",
                    color = if (pandal.isVisited) EmeraldGreen else CrimsonRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            (if (pandal.isVisited) EmeraldGreen else CrimsonRed).copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dual Action Buttons: Mark Visited & Turn-by-Turn Google Maps Handoff
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // [🙏 Mark Visited]
                OutlinedButton(
                    onClick = onToggleVisited,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("toggle_visited_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (pandal.isVisited) EmeraldGreen else SaffronPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (pandal.isVisited) Icons.Default.Check else Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (pandal.isVisited) "Mark Unvisited" else "Mark Visited",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // [📱 Open Turn-by-Turn in Google Maps]
                Button(
                    onClick = onHandoffGoogleMaps,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("open_google_maps_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = Color(0xFF1A1A1A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Turn-by-Turn in Maps",
                        color = Color(0xFF1A1A1A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceDetailAddSheet(
    place: PandalItem,
    onAddClicked: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("place_detail_add_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PLACE DETAILS",
                color = SaffronPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = place.name,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = place.address,
            color = TextSecondary,
            fontSize = 14.sp
        )

        if (place.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = place.description,
                color = TextPrimary.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Coordinates: ${String.format("%.4f", place.latitude)}, ${String.format("%.4f", place.longitude)}",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Prominent [➕ Add to Checklist] Button
        Button(
            onClick = onAddClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("add_to_checklist_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color(0xFF1A1A1A),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add to Checklist",
                color = Color(0xFF1A1A1A),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Native Handoff requirement:
 * Fires Intent: https://www.google.com/maps/dir/?api=1&destination=lat,lng&travelmode=walking
 */
private fun openGoogleMapsWalkingDirections(context: Context, latitude: Double, longitude: Double) {
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude&travelmode=walking")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to browser or any map handler if Google Maps app is not installed
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(fallbackIntent)
    }
}

private fun formatDistance(meters: Float): String {
    return if (meters >= 1000) {
        String.format("%.1f km", meters / 1000f)
    } else {
        "${meters.toInt()} m"
    }
}
