package com.erolgizlice.routetracker.data.address

import com.erolgizlice.routetracker.core.route.AddressLookup
import com.erolgizlice.routetracker.core.route.RoutePoint
import com.erolgizlice.routetracker.data.route.RouteDao

internal class RoomAddressLookup(
    private val resolver: GeocoderAddressResolver,
    private val dao: RouteDao,
) : AddressLookup {

    override suspend fun ensureAddress(point: RoutePoint): String? {
        point.address?.let { return it }
        val address = resolver.resolve(point.latitude, point.longitude) ?: return null
        // UPDATE only: if a reset deleted the point meanwhile, this touches no row instead of recreating it (D14).
        dao.updateAddress(point.id, address)
        return address
    }
}
