package com.erolgizlice.routetracker.core.route

/** A marker on the route: a location that was at least the gate distance away from the previous one. */
data class RoutePoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val recordedAtEpochMillis: Long,
    /** Null until reverse geocoding succeeds; recording never waits for it. */
    val address: String?,
)
