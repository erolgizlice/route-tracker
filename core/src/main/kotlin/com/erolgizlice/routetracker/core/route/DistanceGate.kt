package com.erolgizlice.routetracker.core.route

/**
 * The app's single business rule: a fix becomes a marker only when it is at least [minDistanceMeters]
 * away from the **anchor**, the last *recorded* point, never the previous GPS fix.
 *
 * Measuring fix-to-fix fails both ways: GPS jitter adds markers while standing still, and a slow walk
 * made of sub-threshold steps never adds one. The anchor is the newest persisted point, so the rule
 * keeps holding across process death and route resets without any extra state.
 *
 * The platform's `setMinUpdateDistanceMeters` is only a battery hint; this gate is the rule.
 */
class DistanceGate(
    private val minDistanceMeters: Double = DEFAULT_MIN_DISTANCE_METERS,
    private val maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS,
) {

    fun evaluate(anchor: RoutePoint?, fix: LocationFix): Decision {
        // Checked before the anchor: an inaccurate first fix must not become the start of the route,
        // or the next accurate fix would look far away and drop a marker without any movement.
        // Written as !(x <= max) so that a NaN accuracy is rejected too.
        val accuracy = fix.accuracyMeters
        if (accuracy == null || !(accuracy <= maxAccuracyMeters)) return Decision.RejectInaccurate

        if (anchor == null) return Decision.Record

        val distance = distanceMeters(anchor.latitude, anchor.longitude, fix.latitude, fix.longitude)
        return if (distance >= minDistanceMeters) Decision.Record else Decision.RejectTooClose(distance)
    }

    sealed interface Decision {
        data object Record : Decision
        data object RejectInaccurate : Decision
        data class RejectTooClose(val distanceMeters: Double) : Decision
    }

    companion object {
        const val DEFAULT_MIN_DISTANCE_METERS = 100.0

        /** Half the rule's distance: a fix as uncertain as the rule itself makes noise equal to signal. */
        const val DEFAULT_MAX_ACCURACY_METERS = 50f
    }
}
