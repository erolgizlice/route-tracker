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

    /** State owned by this screen; route and session state come from their own sources. */
    private data class ScreenState(
        val selectedPointId: Long? = null,
        val addressStatus: AddressStatus = AddressStatus.Idle,
        val hasLocationPermission: Boolean = false,
        val issue: TrackingIssue? = null,
        val isResetConfirmationVisible: Boolean = false,
        val notice: TrackingNotice? = null,
    )

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

    fun onIntent(intent: TrackingIntent) {
        when (intent) {
            TrackingIntent.StartClicked ->
                if (locationAccess.hasPreciseLocationPermission()) startIfLocationEnabled()
                else sendEffect(TrackingEffect.RequestLocationPermissions)

            is TrackingIntent.PermissionResult -> onPermissionResult(intent.canAskAgain)
            TrackingIntent.StopClicked -> trackingController.stop()

            TrackingIntent.IssueActionClicked -> when (screen.value.issue?.action) {
                IssueAction.RequestPermission -> sendEffect(TrackingEffect.RequestLocationPermissions)
                IssueAction.OpenAppSettings -> sendEffect(TrackingEffect.OpenAppSettings)
                IssueAction.OpenLocationSettings -> sendEffect(TrackingEffect.OpenLocationSettings)
                null -> Unit
            }
            TrackingIntent.IssueDismissed -> screen.update { it.copy(issue = null) }
            TrackingIntent.ScreenResumed -> onScreenResumed()

            TrackingIntent.ResetClicked -> screen.update { it.copy(isResetConfirmationVisible = true) }
            TrackingIntent.ResetDismissed -> screen.update { it.copy(isResetConfirmationVisible = false) }
            TrackingIntent.ResetConfirmed -> {
                screen.update { it.copy(isResetConfirmationVisible = false) }
                // Tracking, if on, continues: the next accurate fix starts the new route (D15).
                viewModelScope.launch { routeRepository.reset() }
            }

            is TrackingIntent.MarkerClicked -> {
                screen.update { it.copy(selectedPointId = intent.pointId, addressStatus = AddressStatus.Idle) }
                resolveAddress(intent.pointId)
            }
            TrackingIntent.SelectionDismissed ->
                screen.update { it.copy(selectedPointId = null, addressStatus = AddressStatus.Idle) }
            TrackingIntent.RetryAddressClicked -> screen.value.selectedPointId?.let(::resolveAddress)
            TrackingIntent.NoticeDismissed -> screen.update { it.copy(notice = null) }
        }
    }

    private fun onPermissionResult(canAskAgain: Boolean) {
        val previousIssue = screen.value.issue
        screen.update { it.copy(hasLocationPermission = locationAccess.hasAnyLocationPermission()) }
        when {
            locationAccess.hasPreciseLocationPermission() -> startIfLocationEnabled()
            // Approximate location snaps to a grid of about 2 km: useless for a 100 m rule (D16).
            locationAccess.hasAnyLocationPermission() -> {
                // Measured on Android 16 (D18): right after the user picks "approximate", the rationale
                // for FINE is already false, yet the system still shows the upgrade dialog once. So false
                // means "blocked" only when this result answers a request that already asked to upgrade.
                // Blocked counts too, or pressing Start while blocked would flip the card back to "Allow".
                val upgradeAlreadyRequested = previousIssue == TrackingIssue.PreciseLocationDenied ||
                    previousIssue == TrackingIssue.PreciseLocationBlocked
                showIssue(
                    if (canAskAgain || !upgradeAlreadyRequested) TrackingIssue.PreciseLocationDenied
                    else TrackingIssue.PreciseLocationBlocked,
                )
            }
            else -> showIssue(if (canAskAgain) TrackingIssue.LocationDenied else TrackingIssue.LocationBlocked)
        }
    }

    private fun startIfLocationEnabled() {
        if (!locationAccess.isLocationEnabled()) {
            showIssue(TrackingIssue.LocationDisabled)
            return
        }
        screen.update { it.copy(issue = null) }
        trackingController.start()
    }

    /** The user may have changed permissions or the location switch in Settings while we were away. */
    private fun onScreenResumed() {
        screen.update { current ->
            val resolved = when (current.issue) {
                TrackingIssue.LocationDisabled -> locationAccess.isLocationEnabled()
                null -> false
                else -> locationAccess.hasPreciseLocationPermission()
            }
            current.copy(
                hasLocationPermission = locationAccess.hasAnyLocationPermission(),
                issue = current.issue.takeUnless { resolved },
            )
        }
    }

    /**
     * The address is normally resolved when the point is recorded. If that failed (offline, rate limited),
     * a tap tries again. The result is stored and reaches the card through the route flow; the lookup is
     * not cancelled when the card closes, so the stored address is not lost.
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

    private fun showIssue(issue: TrackingIssue) = screen.update { it.copy(issue = issue) }

    private fun sendEffect(effect: TrackingEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }
}
