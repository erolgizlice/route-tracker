package com.erolgizlice.routetracker.feature.tracking

/**
 * State owned by this screen; the route and the session state come from their own sources and are joined
 * into [TrackingState] by the ViewModel.
 */
internal data class ScreenState(
    val selectedPointId: Long? = null,
    val addressStatus: AddressStatus = AddressStatus.Idle,
    val hasLocationPermission: Boolean = false,
    val issue: TrackingIssue? = null,
    val isResetConfirmationVisible: Boolean = false,
    val notice: TrackingNotice? = null,
)

/**
 * What the platform says about location, read once per intent by the ViewModel and handed to [reduce].
 * Asking for it here rather than inside the reduction is what keeps the reduction pure and testable on
 * the JVM: these three answers are the only thing the transitions need from Android.
 */
internal data class LocationSnapshot(
    val hasPrecise: Boolean,
    val hasAny: Boolean,
    val isEnabled: Boolean,
)

/** Tracking can begin right now: precise permission held and the device-wide switch on (D16). */
internal fun LocationSnapshot.canStartTracking(): Boolean = hasPrecise && isEnabled

/**
 * The screen's state transitions, all of them, as one pure function: no Android, no coroutines, no
 * repository. The side effects that go with a transition - starting or stopping the service, resetting
 * the route, resolving an address, asking for permissions - stay in the ViewModel and run after this.
 */
internal fun ScreenState.reduce(intent: TrackingIntent, location: LocationSnapshot): ScreenState =
    when (intent) {
        // Without precise permission the state does not move: the permission dialog answers this press,
        // and its result arrives as PermissionResult below.
        TrackingIntent.StartClicked -> if (location.hasPrecise) readyToStart(location) else this

        is TrackingIntent.PermissionResult -> {
            val granted = copy(hasLocationPermission = location.hasAny)
            when {
                location.hasPrecise -> granted.readyToStart(location)
                // Approximate location snaps to a grid of about 2 km: useless for a 100 m rule (D16).
                location.hasAny -> {
                    // Measured on Android 16 (D18): right after the user picks "approximate", the rationale
                    // for FINE is already false, yet the system still shows the upgrade dialog once. So false
                    // means "blocked" only when this result answers a request that already asked to upgrade.
                    // Blocked counts too, or pressing Start while blocked would flip the card back to "Allow".
                    val upgradeAlreadyRequested = issue == TrackingIssue.PreciseLocationDenied ||
                        issue == TrackingIssue.PreciseLocationBlocked
                    granted.copy(
                        issue = if (intent.canAskAgain || !upgradeAlreadyRequested) {
                            TrackingIssue.PreciseLocationDenied
                        } else {
                            TrackingIssue.PreciseLocationBlocked
                        },
                    )
                }
                else -> granted.copy(
                    issue = if (intent.canAskAgain) TrackingIssue.LocationDenied else TrackingIssue.LocationBlocked,
                )
            }
        }

        // The user may have changed permissions or the location switch in Settings while we were away.
        TrackingIntent.ScreenResumed -> {
            val resolved = when (issue) {
                TrackingIssue.LocationDisabled -> location.isEnabled
                null -> false
                else -> location.hasPrecise
            }
            copy(hasLocationPermission = location.hasAny, issue = issue.takeUnless { resolved })
        }

        TrackingIntent.IssueDismissed -> copy(issue = null)

        TrackingIntent.ResetClicked -> copy(isResetConfirmationVisible = true)
        TrackingIntent.ResetDismissed -> copy(isResetConfirmationVisible = false)
        TrackingIntent.ResetConfirmed -> copy(isResetConfirmationVisible = false)

        is TrackingIntent.MarkerClicked ->
            copy(selectedPointId = intent.pointId, addressStatus = AddressStatus.Idle)
        TrackingIntent.SelectionDismissed -> copy(selectedPointId = null, addressStatus = AddressStatus.Idle)

        TrackingIntent.NoticeDismissed -> copy(notice = null)

        // Only effects: the issue's own action, the lookup for the selected point, stopping the service.
        TrackingIntent.IssueActionClicked,
        TrackingIntent.RetryAddressClicked,
        TrackingIntent.StopClicked,
        -> this
    }

/** Precise permission is held, so the device-wide switch is the only thing that can still be wrong. */
private fun ScreenState.readyToStart(location: LocationSnapshot): ScreenState =
    copy(issue = if (location.isEnabled) null else TrackingIssue.LocationDisabled)
