package com.erolgizlice.routetracker.core.tracking

import kotlinx.coroutines.flow.Flow

interface TrackingController {

    /** Whether a tracking session is active. Persisted, so it is still known after process death. */
    val isTracking: Flow<Boolean>

    /** Starts a session. Call only while the app is in the foreground and holds precise location. */
    fun start()

    fun stop()

    /**
     * Ends a session that is marked active although no tracking service runs in this process: after a
     * force-stop or a reboot no app code runs to clear it. Returns true when such a session was ended.
     */
    suspend fun endStaleSession(): Boolean
}
