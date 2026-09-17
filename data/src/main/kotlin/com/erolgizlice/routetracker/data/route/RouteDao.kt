package com.erolgizlice.routetracker.data.route

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface RouteDao {

    // Ordered by insertion (id), not by timestamp: a wall-clock change must not reorder the route.
    @Query("SELECT * FROM route_points ORDER BY id")
    fun observeAll(): Flow<List<RoutePointEntity>>

    @Query("SELECT * FROM route_points ORDER BY id DESC LIMIT 1")
    suspend fun last(): RoutePointEntity?

    @Insert
    suspend fun insert(point: RoutePointEntity): Long

    /** Returns the number of rows changed: 0 when the point was deleted by a reset in the meantime. */
    @Query("UPDATE route_points SET address = :address WHERE id = :id")
    suspend fun updateAddress(id: Long, address: String): Int

    @Query("DELETE FROM route_points")
    suspend fun deleteAll()
}
