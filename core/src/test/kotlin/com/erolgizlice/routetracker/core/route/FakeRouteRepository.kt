package com.erolgizlice.routetracker.core.route

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield

internal class FakeRouteRepository(initial: List<RoutePoint> = emptyList()) : RouteRepository {

    private val route = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val points: List<RoutePoint> get() = route.value

    override fun observeRoute(): Flow<List<RoutePoint>> = route

    override suspend fun lastPoint(): RoutePoint? {
        // A real database read suspends. Yielding here lets concurrent callers interleave between
        // reading the anchor and writing, which is exactly the race the use case must prevent.
        yield()
        return route.value.lastOrNull()
    }

    override suspend fun add(fix: LocationFix): RoutePoint {
        yield()
        val point = RoutePoint(
            id = nextId++,
            latitude = fix.latitude,
            longitude = fix.longitude,
            recordedAtEpochMillis = fix.timestampEpochMillis,
            address = null,
        )
        route.value = route.value + point
        return point
    }

    override suspend fun reset() {
        route.value = emptyList()
    }
}
