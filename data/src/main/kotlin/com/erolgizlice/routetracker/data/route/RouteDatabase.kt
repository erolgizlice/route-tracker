package com.erolgizlice.routetracker.data.route

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RoutePointEntity::class], version = 1, exportSchema = true)
internal abstract class RouteDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
}
