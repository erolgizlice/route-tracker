package com.erolgizlice.routetracker.core.route

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8

/**
 * Great-circle distance (haversine) on a spherical Earth.
 *
 * Kept free of android.location.Location so the 100 m rule is testable on the JVM. The sphere
 * differs from the WGS84 ellipsoid by at most ~0.5%, i.e. half a meter at the 100 m threshold.
 */
fun distanceMeters(fromLatitude: Double, fromLongitude: Double, toLatitude: Double, toLongitude: Double): Double {
    val deltaLatitude = Math.toRadians(toLatitude - fromLatitude)
    val deltaLongitude = Math.toRadians(toLongitude - fromLongitude)
    val a = sin(deltaLatitude / 2).pow(2) +
        cos(Math.toRadians(fromLatitude)) * cos(Math.toRadians(toLatitude)) * sin(deltaLongitude / 2).pow(2)
    // Rounding can push `a` a hair above 1 for near-antipodal points, which would make asin return NaN.
    return 2 * EARTH_MEAN_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}
