package com.erolgizlice.routetracker

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.erolgizlice.routetracker.feature.tracking.TrackingRoute

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars on both edges: the map is the background of this screen, and the scrim that keeps
        // the navigation bar legible is drawn by the app itself, in its own colour (D25).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The map is the background of this screen, so it should run to the bottom edge. Without this,
            // the system paints a scrim behind the navigation bar: a pale strip on a three-button device
            // and nothing at all on a device with a gesture bar (D25).
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            MaterialTheme {
                TrackingRoute(hasMapsApiKey = BuildConfig.HAS_MAPS_API_KEY)
            }
        }
    }
}
