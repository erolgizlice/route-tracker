package com.erolgizlice.routetracker.core.route

import kotlinx.coroutines.flow.Flow

interface RouteRepository {

    /** The persisted route in recording order. Survives process death until [reset]. */
    fun observeRoute(): Flow<List<RoutePoint>>

    /** The newest recorded point: the anchor for the distance rule. */
    suspend fun lastPoint(): RoutePoint?

    /** Persists [fix] as a new point stamped with the fix's own time, and returns it with its id. */
    suspend fun add(fix: LocationFix): RoutePoint

    suspend fun reset()
}
