package com.erolgizlice.routetracker

import android.app.Application
import com.erolgizlice.routetracker.data.di.dataModule
import com.erolgizlice.routetracker.feature.tracking.di.trackingModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RouteTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application-level on purpose: a START_STICKY restart creates the process without any
        // Activity, and the tracking service still needs its dependencies.
        startKoin {
            androidContext(this@RouteTrackerApp)
            modules(dataModule, trackingModule)
        }
    }
}
