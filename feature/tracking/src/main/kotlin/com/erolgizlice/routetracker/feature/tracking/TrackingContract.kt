package com.erolgizlice.routetracker.feature.tracking

import com.erolgizlice.routetracker.core.route.RoutePoint

data class TrackingState(
    val isLoading: Boolean = true,
    val points: List<RoutePoint> = emptyList(),
    /** Derived from [points], so a route reset closes the details card by construction. */
    val selectedPoint: RoutePoint? = null,
)

sealed interface TrackingIntent {
    data class MarkerClicked(val pointId: Long) : TrackingIntent
    data object SelectionDismissed : TrackingIntent
}
