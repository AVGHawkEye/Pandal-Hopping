package com.example.model

data class PandalItem(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = STATUS_UNVISITED,
    val notes: String = "",
    val description: String = "",
    val category: String = "Pandal",
    val distanceMeters: Float? = null,
    val walkingDurationMinutes: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isVisited: Boolean
        get() = status.equals(STATUS_VISITED, ignoreCase = true)

    companion object {
        const val STATUS_UNVISITED = "UNVISITED"
        const val STATUS_VISITED = "VISITED"
    }
}
