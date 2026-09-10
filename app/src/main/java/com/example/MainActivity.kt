package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.AppScreen
import com.example.ui.ChecklistScreen
import com.example.ui.MapScreen
import com.example.ui.PandalViewModel
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng

class MainActivity : ComponentActivity() {
    private val viewModel: PandalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                PandalHopperApp(viewModel = viewModel, activity = this)
            }
        }
    }
}

@Composable
fun PandalHopperApp(viewModel: PandalViewModel, activity: ComponentActivity) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val pandals by viewModel.pandals.collectAsState()
    val unvisitedCount = pandals.count { !it.isVisited }

    // Request Location Permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            fetchUserLocation(activity, viewModel)
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            fetchUserLocation(activity, viewModel)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextPrimary,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.MAP,
                    onClick = { viewModel.setScreen(AppScreen.MAP) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Live Map"
                        )
                    },
                    label = {
                        Text(
                            text = "Live Map",
                            fontSize = 12.sp,
                            fontWeight = if (currentScreen == AppScreen.MAP) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1A1A1A),
                        selectedTextColor = SaffronPrimary,
                        indicatorColor = SaffronPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_item_map")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.CHECKLIST,
                    onClick = { viewModel.setScreen(AppScreen.CHECKLIST) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (unvisitedCount > 0) {
                                    Badge(
                                        containerColor = SaffronPrimary,
                                        contentColor = Color(0xFF1A1A1A)
                                    ) {
                                        Text("$unvisitedCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = "Checklist"
                            )
                        }
                    },
                    label = {
                        Text(
                            text = "Checklist",
                            fontSize = 12.sp,
                            fontWeight = if (currentScreen == AppScreen.CHECKLIST) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1A1A1A),
                        selectedTextColor = SaffronPrimary,
                        indicatorColor = SaffronPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_item_checklist")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.MAP -> MapScreen(viewModel = viewModel)
                AppScreen.CHECKLIST -> ChecklistScreen(viewModel = viewModel)
            }
        }
    }
}

private fun fetchUserLocation(activity: ComponentActivity, viewModel: PandalViewModel) {
    try {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                viewModel.setUserLocation(LatLng(location.latitude, location.longitude))
            }
        }
    } catch (e: SecurityException) {
        // Ignored, location falls back to central Mumbai
    }
}

// Retained for tests and preview compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
