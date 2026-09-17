package com.erolgizlice.routetracker.data.address

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/** Reverse geocoding through the platform Geocoder. Every failure is an expected outcome: null. */
internal class GeocoderAddressResolver(private val context: Context) {

    suspend fun resolve(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        // withTimeoutOrNull rethrows cancellation of the caller; only its own timeout becomes null.
        return withTimeoutOrNull(TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                continuation.resume(addresses.firstOrNull()?.format())
                            }

                            override fun onError(errorMessage: String?) {
                                Log.d(TAG, "Geocoding failed: $errorMessage")
                                continuation.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) { resolveBlocking(geocoder, latitude, longitude) }
            }
        }
    }

    private fun resolveBlocking(geocoder: Geocoder, latitude: Double, longitude: Double): String? = try {
        @Suppress("DEPRECATION") // the listener overload only exists on API 33+
        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.format()
    } catch (e: IOException) {
        // Offline or the backing service is unavailable: expected, not an error to surface.
        Log.d(TAG, "Geocoding failed", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.d(TAG, "Geocoding rejected the coordinates", e)
        null
    }

    private fun Address.format(): String? =
        getAddressLine(0) ?: listOfNotNull(thoroughfare, subLocality, locality).joinToString(", ").ifEmpty { null }

    private companion object {
        const val TAG = "GeocoderAddressResolver"
        const val TIMEOUT_MS = 10_000L
    }
}
