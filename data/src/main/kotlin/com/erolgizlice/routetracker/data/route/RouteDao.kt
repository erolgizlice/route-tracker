package com.erolgizlice.routetracker.data.route

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface RouteDao {

    // Ordered by insertion (id), not by timestamp: a wall-clock change must not reorder the route.
    @Query("SELECT * FROM route_points ORDER BY id")
    fun observeAll(): Flow<List<RoutePointEntity>>

    @Query("DELETE FROM route_points")
    suspend fun deleteAll()
}
