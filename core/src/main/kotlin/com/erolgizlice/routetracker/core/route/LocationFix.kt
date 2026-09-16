package com.erolgizlice.routetracker.core.route

/** A raw location reading from the platform, before [DistanceGate] decides whether it becomes a [RoutePoint]. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    /** Horizontal accuracy radius in meters, or null when the platform does not report one. */
    val accuracyMeters: Float?,
    val timestampEpochMillis: Long,
)
