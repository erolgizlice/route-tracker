package com.erolgizlice.routetracker.data.route

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.erolgizlice.routetracker.core.route.RoutePoint

@Entity(tableName = "route_points")
internal data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "recorded_at") val recordedAtEpochMillis: Long,
    val address: String?,
)

internal fun RoutePointEntity.toDomain() = RoutePoint(
    id = id,
    latitude = latitude,
    longitude = longitude,
    recordedAtEpochMillis = recordedAtEpochMillis,
    address = address,
)
