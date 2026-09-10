package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

enum class ChecklistFilter {
    ALL,
    UNVISITED,
    VISITED
}

@Composable
fun ChecklistScreen(
    viewModel: PandalViewModel,
    modifier: Modifier = Modifier
) {
    val pandals by viewModel.pandals.collectAsState()
    var currentFilter by remember { mutableStateOf(ChecklistFilter.ALL) }
    var pandalToDelete by remember { mutableStateOf<PandalItem?>(null) }
    var pandalToEditNotes by remember { mutableStateOf<PandalItem?>(null) }

    val visitedCount = pandals.count { it.isVisited }
    val totalCount = pandals.size
    val progress = if (totalCount > 0) visitedCount.toFloat() / totalCount else 0f

    val filteredPandals = when (currentFilter) {
        ChecklistFilter.ALL -> pandals
        ChecklistFilter.UNVISITED -> pandals.filter { !it.isVisited }
        ChecklistFilter.VISITED -> pandals.filter { it.isVisited }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("checklist_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                // Festive Header & Progress Tracker
                ChecklistProgressHeader(
                    visitedCount = visitedCount,
                    totalCount = totalCount,
                    progress = progress
                )
            }

            item {
                // Filter Chips (All, Unvisited, Visited)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = currentFilter == ChecklistFilter.ALL,
                        onClick = { currentFilter = ChecklistFilter.ALL },
                        label = { Text("All ($totalCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SaffronPrimary,
                            selectedLabelColor = Color(0xFF1A1A1A),
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = currentFilter == ChecklistFilter.UNVISITED,
                        onClick = { currentFilter = ChecklistFilter.UNVISITED },
                        label = { Text("Unvisited (${totalCount - visitedCount})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrimsonRed,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = currentFilter == ChecklistFilter.VISITED,
                        onClick = { currentFilter = ChecklistFilter.VISITED },
                        label = { Text("Visited ($visitedCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color(0xFF1A1A1A),
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            if (filteredPandals.isEmpty()) {
                item {
                    EmptyChecklistState(currentFilter = currentFilter)
                }
            } else {
                items(filteredPandals, key = { it.id }) { pandal ->
                    PandalChecklistCard(
                        pandal = pandal,
                        onToggleVisited = { viewModel.toggleVisitStatus(pandal) },
                        onFocusRoute = { viewModel.focusPandalRoute(pandal) },
                        onDelete = { pandalToDelete = pandal },
                        onEditNotes = { pandalToEditNotes = pandal }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // Delete Confirmation Dialog
        pandalToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { pandalToDelete = null },
                title = { Text("Delete from Checklist?") },
                text = { Text("Are you sure you want to remove \"${item.name}\"?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deletePandal(item.id)
                            pandalToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pandalToDelete = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        }

        // Edit Notes Dialog
        pandalToEditNotes?.let { item ->
            var notesText by remember { mutableStateOf(item.notes) }
            AlertDialog(
                onDismissRequest = { pandalToEditNotes = null },
                title = { Text("Pandal Notes • ${item.name}", fontSize = 16.sp) },
                text = {
                    Column {
                        Text(
                            "Add Darshan timings, line tips, or personal memories:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            placeholder = { Text("e.g. VIP line gate 2, best at 5 AM...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateNotes(item.id, notesText)
                            pandalToEditNotes = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                    ) {
                        Text("Save Notes", color = Color(0xFF1A1A1A))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pandalToEditNotes = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}

@Composable
private fun ChecklistProgressHeader(
    visitedCount: Int,
    totalCount: Int,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PANDAL DARSHAN PROGRESS",
                        color = SaffronPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "$visitedCount of $totalCount Pandals Visited",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = EmeraldGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = EmeraldGreen,
                trackColor = DarkSurfaceVariant
            )
        }
    }
}

@Composable
private fun PandalChecklistCard(
    pandal: PandalItem,
    onToggleVisited: () -> Unit,
    onFocusRoute: () -> Unit,
    onDelete: () -> Unit,
    onEditNotes: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("checklist_card_${pandal.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (pandal.isVisited) EmeraldGreen.copy(alpha = 0.35f) else DarkCardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Pandal Name & Visit Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pandal.name,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pandal.address,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Visit Status Badge
                Text(
                    text = if (pandal.isVisited) "VISITED" else "UNVISITED",
                    color = if (pandal.isVisited) EmeraldGreen else CrimsonRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            (if (pandal.isVisited) EmeraldGreen else CrimsonRed).copy(alpha = 0.14f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Row: Distance & Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsWalk,
                    contentDescription = null,
                    tint = SaffronPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val distText = if (pandal.distanceMeters != null) {
                    val formatted = if (pandal.distanceMeters >= 1000) {
                        String.format("%.1f km away", pandal.distanceMeters / 1000f)
                    } else {
                        "${pandal.distanceMeters.toInt()} m away"
                    }
                    "$formatted (~${pandal.walkingDurationMinutes ?: 1} min walk)"
                } else {
                    "Live Distance Calculating..."
                }

                Text(
                    text = distText,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = pandal.category,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(DarkSurfaceVariant, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // User Notes Section
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Notes,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (pandal.notes.isNotBlank()) pandal.notes else "No notes added yet (tap to add notes)...",
                    color = if (pandal.notes.isNotBlank()) TextPrimary else TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onEditNotes,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Notes",
                        tint = SaffronPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Spacious Action Buttons: [🙏 Mark Visited], [🎯 Focus Route], [🗑️ Delete]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [🙏 Mark Visited]
                OutlinedButton(
                    onClick = onToggleVisited,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(44.dp)
                        .testTag("action_toggle_visited_${pandal.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (pandal.isVisited) EmeraldGreen else SaffronPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (pandal.isVisited) Icons.Default.CheckCircle else Icons.Outlined.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (pandal.isVisited) "Visited" else "Mark Visited",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // [🎯 Focus Route]
                Button(
                    onClick = onFocusRoute,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
                        .testTag("action_focus_route_${pandal.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = Color(0xFF1A1A1A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Focus Route",
                        color = Color(0xFF1A1A1A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // [🗑️ Delete]
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(44.dp)
                        .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .testTag("action_delete_${pandal.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete pandal",
                        tint = CrimsonRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChecklistState(currentFilter: ChecklistFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when (currentFilter) {
                ChecklistFilter.ALL -> "No pandals in your checklist yet"
                ChecklistFilter.UNVISITED -> "All pandals have been visited! 🙏"
                ChecklistFilter.VISITED -> "You haven't marked any pandal as visited yet"
            },
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Use the search bar on the Map view to discover and add real-world pandals.",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}
