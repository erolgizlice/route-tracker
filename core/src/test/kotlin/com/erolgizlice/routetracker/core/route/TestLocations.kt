package com.erolgizlice.routetracker.core.route

import kotlin.math.cos

internal const val ORIGIN_LATITUDE = 41.0082
internal const val ORIGIN_LONGITUDE = 28.9784

private val metersPerDegreeLatitude = EARTH_MEAN_RADIUS_METERS * Math.PI / 180
private val metersPerDegreeLongitude = metersPerDegreeLatitude * cos(Math.toRadians(ORIGIN_LATITUDE))

/** A fix displaced from the origin by the given meters (small-offset approximation, exact enough here). */
internal fun fixAt(
    north: Double,
    east: Double = 0.0,
    accuracy: Float? = 10f,
    timestamp: Long = 0,
) = LocationFix(
    latitude = ORIGIN_LATITUDE + north / metersPerDegreeLatitude,
    longitude = ORIGIN_LONGITUDE + east / metersPerDegreeLongitude,
    accuracyMeters = accuracy,
    timestampEpochMillis = timestamp,
)

internal fun RoutePoint.metersNorthOfOrigin(): Double = (latitude - ORIGIN_LATITUDE) * metersPerDegreeLatitude
