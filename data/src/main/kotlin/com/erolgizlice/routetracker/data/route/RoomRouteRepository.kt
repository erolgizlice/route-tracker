package com.erolgizlice.routetracker.data.route

import com.erolgizlice.routetracker.core.route.LocationFix
import com.erolgizlice.routetracker.core.route.RoutePoint
import com.erolgizlice.routetracker.core.route.RouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomRouteRepository(private val dao: RouteDao) : RouteRepository {

    override fun observeRoute(): Flow<List<RoutePoint>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun lastPoint(): RoutePoint? = dao.last()?.toDomain()

    override suspend fun add(fix: LocationFix): RoutePoint {
        val entity = RoutePointEntity(
            latitude = fix.latitude,
            longitude = fix.longitude,
            recordedAtEpochMillis = fix.timestampEpochMillis,
            address = null,
        )
        return entity.copy(id = dao.insert(entity)).toDomain()
    }

    override suspend fun reset() = dao.deleteAll()
}
