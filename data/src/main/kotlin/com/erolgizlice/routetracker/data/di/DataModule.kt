package com.erolgizlice.routetracker.data.di

import androidx.room.Room
import com.erolgizlice.routetracker.core.route.DistanceGate
import com.erolgizlice.routetracker.core.route.RecordFixUseCase
import com.erolgizlice.routetracker.core.route.RouteRepository
import com.erolgizlice.routetracker.data.route.RoomRouteRepository
import com.erolgizlice.routetracker.data.route.RouteDatabase
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
}
