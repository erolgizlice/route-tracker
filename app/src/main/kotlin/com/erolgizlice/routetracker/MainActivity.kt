package com.erolgizlice.routetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.erolgizlice.routetracker.feature.tracking.TrackingRoute

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                TrackingRoute(hasMapsApiKey = BuildConfig.HAS_MAPS_API_KEY)
            }
        }
    }
}
