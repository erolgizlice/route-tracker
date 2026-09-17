package com.erolgizlice.routetracker.data.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import com.erolgizlice.routetracker.core.route.AddressLookup
import com.erolgizlice.routetracker.core.route.LocationFix
import com.erolgizlice.routetracker.core.route.RecordFixUseCase
import com.erolgizlice.routetracker.core.route.RouteRepository
import com.erolgizlice.routetracker.core.tracking.LocationAccess
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the process alive and receives location while tracking, including in the background.
 *
 * Started with `startService` from the foreground app, then promoted with `startForeground`. Not with
 * `startForegroundService`: that call obliges the service to reach `startForeground` within seconds,
 * but a location-typed `startForeground` throws without a location permission, so a session that must
 * end for lack of permission would have no legal way out. See docs/decisions.md D17.
 */
class TrackingService : Service() {

    private val sessionStore: TrackingSessionStore by inject()
    private val locationAccess: LocationAccess by inject()
    private val recordFix: RecordFixUseCase by inject()
    private val routeRepository: RouteRepository by inject()
    private val notifications: TrackingNotifications by inject()
    private val addressLookup: AddressLookup by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Commands run strictly one after another, so a quick stop-then-start cannot interleave.
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val fixes = Channel<LocationFix>(Channel.UNLIMITED)

    private lateinit var locationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var notificationUpdates: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        scope.launch { for (command in commands) handle(command) }
        scope.launch(Dispatchers.Default) { for (fix in fixes) record(fix) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = when (intent?.action) {
            ACTION_START -> Command.Start(startId, userInitiated = true)
            ACTION_STOP -> Command.Stop(startId)
            // A START_STICKY restart after process death delivers a null intent. Everything needed to
            // resume therefore comes from persistent storage, never from intent extras.
            null -> Command.Start(startId, userInitiated = false)
            else -> null
        }
        command?.let(commands::trySend)
        return START_STICKY
    }

    override fun onDestroy() {
        // First: a callback left registered keeps GPS on for a session that no longer exists.
        removeLocationUpdates()
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.Start -> start(command)
            is Command.Stop -> endSession(command.startId, reason = "stopped by the user", notifyUser = false)
            is Command.Fail -> endSession(startId = null, reason = command.reason, notifyUser = true)
        }
    }

    private suspend fun start(command: Command.Start) {
        if (command.userInitiated) {
            sessionStore.setActive(true)
        } else if (!sessionStore.isActive()) {
            Log.i(TAG, "Restarted without an active session")
            stopSelf(command.startId)
            return
        }

        // 1. Permission, asked directly. On targetSdk 34+ a location-typed startForeground throws without
        //    it, and the fused provider reports a missing permission only asynchronously, so a normal
        //    return from requestLocationUpdates would prove nothing.
        if (!locationAccess.hasAnyLocationPermission()) {
            endSession(command.startId, reason = "no location permission", notifyUser = !command.userInitiated)
            return
        }

        // 2. Foreground, before any location is requested: location access in the background comes from
        //    being a location foreground service, and if that is refused there is nothing to undo.
        if (!startForegroundSafely()) {
            endSession(command.startId, reason = "startForeground was refused", notifyUser = !command.userInitiated)
            return
        }

        // 3. Location updates. Idempotent: a repeated start must not register a second callback.
        if (locationCallback == null) requestLocationUpdates()
        if (notificationUpdates == null) {
            notificationUpdates = scope.launch {
                routeRepository.observeRoute().map { it.size }.distinctUntilChanged()
                    .collect(notifications::updateOngoing)
            }
        }
    }

    /**
     * Returns false instead of throwing. Both refusals happen on the restart path, and a throw out of
     * onStartCommand crashes the app, after which START_STICKY restarts the service into the same throw:
     *  - SecurityException: no location permission (targetSdk 34+).
     *  - ForegroundServiceStartNotAllowedException, an IllegalStateException: started from the background.
     */
    private suspend fun startForegroundSafely(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            TrackingNotifications.ONGOING_ID,
            notifications.ongoing(markerCount = routeRepository.observeRoute().first().size),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "startForeground refused", e)
        false
    } catch (e: IllegalStateException) {
        Log.w(TAG, "startForeground refused", e)
        false
    }

    @SuppressLint("MissingPermission") // checked through LocationAccess immediately before, in start()
    private fun requestLocationUpdates() {
        // High accuracy: balanced power is ~100 m accurate, as coarse as the rule itself.
        // No setMinUpdateDistanceMeters: the platform would measure from the last *delivered* fix, which
        // may be one the gate rejected; the gate measures from the last *recorded* point.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL_MS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // May hold several batched fixes; they are recorded in order through one channel.
                result.locations.forEach { fixes.trySend(it.toFix()) }
            }
        }
        locationCallback = callback
        locationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { error ->
                Log.e(TAG, "Location updates failed", error)
                commands.trySend(Command.Fail("location updates failed: ${error.javaClass.simpleName}"))
            }
    }

    private fun removeLocationUpdates() {
        locationCallback?.let(locationClient::removeLocationUpdates)
        locationCallback = null
    }

    private suspend fun record(fix: LocationFix) {
        when (val result = recordFix(fix)) {
            is RecordFixUseCase.Result.Recorded -> {
                Log.i(TAG, "Recorded point ${result.point.id} (accuracy ${fix.accuracyMeters} m)")
                // Best effort and off the fix channel: recording never waits for the network. A point left
                // without an address is resolved again when its marker is tapped.
                scope.launch(Dispatchers.IO) { addressLookup.ensureAddress(result.point) }
            }
            is RecordFixUseCase.Result.Skipped ->
                Log.d(TAG, "Skipped fix: ${result.decision} (accuracy ${fix.accuracyMeters} m)")
        }
    }

    private suspend fun endSession(startId: Int?, reason: String, notifyUser: Boolean) {
        Log.i(TAG, "Ending session: $reason")
        removeLocationUpdates()
        notificationUpdates?.cancel()
        notificationUpdates = null
        sessionStore.setActive(false)
        if (notifyUser) notifications.showTrackingStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // With a start id, stop only if no newer start arrived meanwhile; that newer start is queued next.
        if (startId != null) stopSelf(startId) else stopSelf()
    }

    private fun Location.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        timestampEpochMillis = time,
    )

    private sealed interface Command {
        data class Start(val startId: Int, val userInitiated: Boolean) : Command
        data class Stop(val startId: Int) : Command
        data class Fail(val reason: String) : Command
    }

    companion object {
        const val ACTION_START = "com.erolgizlice.routetracker.action.START_TRACKING"
        const val ACTION_STOP = "com.erolgizlice.routetracker.action.STOP_TRACKING"

        private const val TAG = "TrackingService"
        private const val UPDATE_INTERVAL_MS = 10_000L
        private const val FASTEST_UPDATE_INTERVAL_MS = 5_000L

        /** Process-local: true only while a service instance exists in this process. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
