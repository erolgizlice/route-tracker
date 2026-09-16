package com.erolgizlice.routetracker.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erolgizlice.routetracker.core.route.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class TrackingViewModel(routeRepository: RouteRepository) : ViewModel() {

    private val selectedPointId = MutableStateFlow<Long?>(null)

    val state: StateFlow<TrackingState> =
        combine(routeRepository.observeRoute(), selectedPointId) { points, selectedId ->
            TrackingState(
                isLoading = false,
                points = points,
                selectedPoint = points.firstOrNull { it.id == selectedId },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingState())

    fun onIntent(intent: TrackingIntent) {
        when (intent) {
            is TrackingIntent.MarkerClicked -> selectedPointId.value = intent.pointId
            TrackingIntent.SelectionDismissed -> selectedPointId.value = null
        }
    }
}
