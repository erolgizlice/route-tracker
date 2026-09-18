package com.erolgizlice.routetracker.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erolgizlice.routetracker.core.route.AddressLookup
import com.erolgizlice.routetracker.core.route.RouteRepository
import com.erolgizlice.routetracker.core.tracking.LocationAccess
import com.erolgizlice.routetracker.core.tracking.TrackingController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrackingViewModel(
    private val routeRepository: RouteRepository,
    private val trackingController: TrackingController,
    private val locationAccess: LocationAccess,
    private val addressLookup: AddressLookup,
) : ViewModel() {

    private val screen = MutableStateFlow(ScreenState(hasLocationPermission = locationAccess.hasAnyLocationPermission()))

    private val effectChannel = Channel<TrackingEffect>(Channel.BUFFERED)
    val effects: Flow<TrackingEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<TrackingState> =
        combine(routeRepository.observeRoute(), trackingController.isTracking, screen) { points, isTracking, screen ->
            TrackingState(
                isLoading = false,
                points = points,
                selectedPoint = points.firstOrNull { it.id == screen.selectedPointId },
                addressStatus = screen.addressStatus,
                isTracking = isTracking,
                hasLocationPermission = screen.hasLocationPermission,
                issue = screen.issue,
                isResetConfirmationVisible = screen.isResetConfirmationVisible,
                notice = screen.notice,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingState())

    init {
        // Runs once per ViewModel, so not on rotation: only on a real app start, where a session marked
        // active without a running service can only mean it was lost while the app was closed.
        viewModelScope.launch {
            if (trackingController.endStaleSession()) {
                screen.update { it.copy(notice = TrackingNotice.SessionEndedWhileClosed) }
            }
        }
    }

    /**
     * The one gate into this screen's state. The transition is [reduce]'s, pure and tested on the JVM; what
     * is left here is the part that has to touch something outside: the service, the repository, the address
     * lookup and the effect channel, all of them after the state has moved.
     */
    fun onIntent(intent: TrackingIntent) {
        val location = locationSnapshot()
        screen.update { it.reduce(intent, location) }
        when (intent) {
            TrackingIntent.StartClicked ->
                if (location.canStartTracking()) trackingController.start()
                else if (!location.hasPrecise) sendEffect(TrackingEffect.RequestLocationPermissions)

            is TrackingIntent.PermissionResult -> if (location.canStartTracking()) trackingController.start()

            TrackingIntent.StopClicked -> trackingController.stop()

            TrackingIntent.IssueActionClicked -> when (screen.value.issue?.action) {
                IssueAction.RequestPermission -> sendEffect(TrackingEffect.RequestLocationPermissions)
                IssueAction.OpenAppSettings -> sendEffect(TrackingEffect.OpenAppSettings)
                IssueAction.OpenLocationSettings -> sendEffect(TrackingEffect.OpenLocationSettings)
                null -> Unit
            }

            // Tracking, if on, continues: the next accurate fix starts the new route (D15).
            TrackingIntent.ResetConfirmed -> viewModelScope.launch { routeRepository.reset() }

            is TrackingIntent.MarkerClicked -> resolveAddress(intent.pointId)
            TrackingIntent.RetryAddressClicked -> screen.value.selectedPointId?.let(::resolveAddress)

            TrackingIntent.IssueDismissed,
            TrackingIntent.ScreenResumed,
            TrackingIntent.ResetClicked,
            TrackingIntent.ResetDismissed,
            TrackingIntent.SelectionDismissed,
            TrackingIntent.NoticeDismissed,
            -> Unit
        }
    }

    /** Asked once per intent, so that every transition sees the same answers (D7). */
    private fun locationSnapshot() = LocationSnapshot(
        hasPrecise = locationAccess.hasPreciseLocationPermission(),
        hasAny = locationAccess.hasAnyLocationPermission(),
        isEnabled = locationAccess.isLocationEnabled(),
    )

    /**
     * The address is normally resolved when the point is recorded. If that failed (offline, rate limited),
     * a tap tries again. The result is stored and reaches the card through the route flow; the lookup is
     * not cancelled when the card closes, so the stored address is not lost.
     *
     * Its two state changes are not a reduction: they answer the lookup, not an intent, and what they may
     * do depends on the route, which this screen does not own.
     */
    private fun resolveAddress(pointId: Long) {
        val point = state.value.points.firstOrNull { it.id == pointId } ?: return
        if (point.address != null) return
        screen.update { it.copy(addressStatus = AddressStatus.Resolving) }
        viewModelScope.launch {
            val address = addressLookup.ensureAddress(point)
            screen.update { current ->
                if (current.selectedPointId != pointId) current // the user has moved on to another marker
                else current.copy(addressStatus = if (address == null) AddressStatus.Unavailable else AddressStatus.Idle)
            }
        }
    }

    private fun sendEffect(effect: TrackingEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }
}
