package com.erolgizlice.routetracker.data.tracking

import android.content.Context
import android.content.Intent
import com.erolgizlice.routetracker.core.tracking.TrackingController
import kotlinx.coroutines.flow.Flow

internal class ServiceTrackingController(
    private val context: Context,
    private val sessionStore: TrackingSessionStore,
) : TrackingController {

    override val isTracking: Flow<Boolean> = sessionStore.isActive

    // startService, not startForegroundService: see TrackingService and docs/decisions.md D17.
    override fun start() = send(TrackingService.ACTION_START)

    override fun stop() = send(TrackingService.ACTION_STOP)

    override suspend fun endStaleSession(): Boolean {
        if (TrackingService.isRunning || !sessionStore.isActive()) return false
        sessionStore.setActive(false)
        return true
    }

    private fun send(action: String) {
        context.startService(Intent(context, TrackingService::class.java).setAction(action))
    }
}
