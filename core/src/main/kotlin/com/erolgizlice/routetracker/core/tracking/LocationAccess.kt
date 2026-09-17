package com.erolgizlice.routetracker.core.tracking

/** Answers from the platform, asked directly each time rather than inferred from earlier results. */
interface LocationAccess {

    /** Required to start tracking: approximate location cannot resolve a 100 m rule. */
    fun hasPreciseLocationPermission(): Boolean

    /** Precise or approximate: what the platform requires to run a location foreground service. */
    fun hasAnyLocationPermission(): Boolean

    /** The device-wide location switch. When it is off, a session receives no fixes at all. */
    fun isLocationEnabled(): Boolean
}
