package com.erolgizlice.routetracker.data.route

import com.erolgizlice.routetracker.core.route.RoutePoint
import com.erolgizlice.routetracker.core.route.RouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomRouteRepository(private val dao: RouteDao) : RouteRepository {

    override fun observeRoute(): Flow<List<RoutePoint>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun reset() = dao.deleteAll()
}
