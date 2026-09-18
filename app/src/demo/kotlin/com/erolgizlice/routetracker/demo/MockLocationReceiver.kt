package com.erolgizlice.routetracker.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.util.Log
import com.erolgizlice.routetracker.core.route.LocationFix
import com.erolgizlice.routetracker.core.route.RouteRepository
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Part of the `demo` build type only, never of `release` or `debug`. Feeds the fused location provider
 * with mock fixes - the one path Play services honours, since its fused client ignores the platform's test
 * providers (measured, D26) - and seeds a route for the thousand-marker clip.
 *
 * The app has to be the selected mock location app:
 * `adb shell appops set com.erolgizlice.routetracker android:mock_location allow`.
 */
class MockLocationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        try {
            when (intent.getStringExtra("cmd")) {
                "on" -> client.setMockMode(true)
                    .addOnCompleteListener { Log.i(TAG, "setMockMode(true) ok=${it.isSuccessful} ${it.exception}") }

                "off" -> client.setMockMode(false)
                    .addOnCompleteListener { Log.i(TAG, "setMockMode(false) ok=${it.isSuccessful} ${it.exception}") }

                "seed" -> seed(intent.getIntExtra("count", 1000), intent.getDoubleExtra("spacing", 20.0))

                else -> {
                    val latitude = intent.getDoubleExtra("lat", 0.0)
                    val longitude = intent.getDoubleExtra("lng", 0.0)
                    val location = Location("fused").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                        accuracy = intent.getFloatExtra("acc", 8f)
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    }
                    client.setMockLocation(location)
                        .addOnCompleteListener { Log.i(TAG, "setMockLocation ok=${it.isSuccessful} ${it.exception}") }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "mock refused", e)
        }
    }

    /** Writes a route straight into storage, which no production path can do, for the stress clip. */
    private fun seed(count: Int, spacingMeters: Double) {
        val repository: RouteRepository = GlobalContext.get().get()
        CoroutineScope(Dispatchers.IO).launch {
            repository.reset()
            val now = System.currentTimeMillis()
            spiral(count, spacingMeters).forEachIndexed { index, (latitude, longitude) ->
                repository.add(LocationFix(latitude, longitude, 8f, now - (count - index) * 10_000L))
            }
            Log.i(TAG, "seeded $count points, $spacingMeters m apart")
        }
    }

    /**
     * An Archimedean spiral around Taksim Square, outside in, so that the point the camera opens on is in
     * the middle of it and all of them are on screen. Made-up coordinates, like the rest of the demo.
     */
    private fun spiral(count: Int, spacing: Double): List<Pair<Double, Double>> {
        val metersPerDegreeLatitude = 111_320.0
        val metersPerDegreeLongitude = 111_320.0 * cos(Math.toRadians(CENTRE_LATITUDE))
        val growthPerRadian = spacing / (2 * PI)
        var theta = 0.0
        return List(count) {
            val radius = growthPerRadian * theta
            val point = CENTRE_LATITUDE + radius * sin(theta) / metersPerDegreeLatitude to
                CENTRE_LONGITUDE + radius * cos(theta) / metersPerDegreeLongitude
            theta += spacing / max(radius, growthPerRadian)
            point
        }.reversed()
    }

    private companion object {
        const val TAG = "MockLocation"
        const val CENTRE_LATITUDE = 41.03630
        const val CENTRE_LONGITUDE = 28.98490
    }
}
