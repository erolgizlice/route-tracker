package com.erolgizlice.routetracker.core.route

import kotlinx.coroutines.flow.Flow

interface RouteRepository {

    /** The persisted route in recording order. Survives process death until [reset]. */
    fun observeRoute(): Flow<List<RoutePoint>>

    suspend fun reset()
}
