package com.erolgizlice.routetracker.core.route

interface AddressLookup {

    /**
     * Returns the address of [point], resolving and storing it first if the point has none.
     * Returns null when it cannot be resolved right now (offline, rate limited, no geocoder, timeout);
     * a later call tries again. Never recreates a point that was deleted in the meantime.
     */
    suspend fun ensureAddress(point: RoutePoint): String?
}
