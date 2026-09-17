package com.erolgizlice.routetracker.data.tracking

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

internal val Context.trackingSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "tracking_session")

/**
 * Whether the user has an active tracking session. This is the only thing a START_STICKY restart can
 * rely on: the restart intent is null, so nothing about the session may live in intent extras.
 */
internal class TrackingSessionStore(private val dataStore: DataStore<Preferences>) {

    val isActive: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[ACTIVE] ?: false }

    suspend fun isActive(): Boolean = isActive.first()

    suspend fun setActive(active: Boolean) {
        dataStore.edit { preferences -> preferences[ACTIVE] = active }
    }

    private companion object {
        val ACTIVE = booleanPreferencesKey("active")
    }
}
