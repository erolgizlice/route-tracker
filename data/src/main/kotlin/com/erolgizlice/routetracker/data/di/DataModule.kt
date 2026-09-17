package com.erolgizlice.routetracker.data.di

import androidx.room.Room
import com.erolgizlice.routetracker.core.route.AddressLookup
import com.erolgizlice.routetracker.core.route.DistanceGate
import com.erolgizlice.routetracker.core.route.RecordFixUseCase
import com.erolgizlice.routetracker.core.route.RouteRepository
import com.erolgizlice.routetracker.core.tracking.LocationAccess
import com.erolgizlice.routetracker.core.tracking.TrackingController
import com.erolgizlice.routetracker.data.address.GeocoderAddressResolver
import com.erolgizlice.routetracker.data.address.RoomAddressLookup
import com.erolgizlice.routetracker.data.route.RoomRouteRepository
import com.erolgizlice.routetracker.data.route.RouteDatabase
import com.erolgizlice.routetracker.data.tracking.AndroidLocationAccess
import com.erolgizlice.routetracker.data.tracking.ServiceTrackingController
import com.erolgizlice.routetracker.data.tracking.TrackingNotifications
import com.erolgizlice.routetracker.data.tracking.TrackingSessionStore
import com.erolgizlice.routetracker.data.tracking.trackingSessionDataStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {
    single { Room.databaseBuilder(androidContext(), RouteDatabase::class.java, "route.db").build() }
    single { get<RouteDatabase>().routeDao() }
    single { RoomRouteRepository(get()) } bind RouteRepository::class

    single { DistanceGate() }
    // Must stay a single: its lock is what makes read-anchor → evaluate → write atomic.
    single { RecordFixUseCase(get(), get()) }

    single { GeocoderAddressResolver(androidContext()) }
    single { RoomAddressLookup(get(), get()) } bind AddressLookup::class

    single { TrackingSessionStore(androidContext().trackingSessionDataStore) }
    single { AndroidLocationAccess(androidContext()) } bind LocationAccess::class
    single { TrackingNotifications(androidContext()) }
    single { ServiceTrackingController(androidContext(), get()) } bind TrackingController::class
}
