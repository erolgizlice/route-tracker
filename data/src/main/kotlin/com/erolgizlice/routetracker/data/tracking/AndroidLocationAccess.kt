package com.erolgizlice.routetracker.data.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.erolgizlice.routetracker.core.tracking.LocationAccess

internal class AndroidLocationAccess(private val context: Context) : LocationAccess {

    override fun hasPreciseLocationPermission(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun hasAnyLocationPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun isLocationEnabled(): Boolean =
        LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
