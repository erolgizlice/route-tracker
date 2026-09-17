package com.erolgizlice.routetracker.feature.tracking

import com.erolgizlice.routetracker.core.route.RoutePoint

data class TrackingState(
    val isLoading: Boolean = true,
    val points: List<RoutePoint> = emptyList(),
    /** Derived from [points], so a route reset closes the details card by construction. */
    val selectedPoint: RoutePoint? = null,
    val isTracking: Boolean = false,
    /** Any location permission; enables the my-location layer, which throws without one. */
    val hasLocationPermission: Boolean = false,
    val issue: TrackingIssue? = null,
    val isResetConfirmationVisible: Boolean = false,
    val notice: TrackingNotice? = null,
)

/** Why tracking could not start, and what the user can do about it. */
enum class TrackingIssue(val action: IssueAction) {
    LocationDenied(IssueAction.RequestPermission),
    LocationBlocked(IssueAction.OpenAppSettings),
    PreciseLocationDenied(IssueAction.RequestPermission),
    PreciseLocationBlocked(IssueAction.OpenAppSettings),
    LocationDisabled(IssueAction.OpenLocationSettings),
}

enum class IssueAction { RequestPermission, OpenAppSettings, OpenLocationSettings }

enum class TrackingNotice {
    /** The session was active but no service was running: a force-stop, a reboot, or a refused restart. */
    SessionEndedWhileClosed,
}

sealed interface TrackingIntent {
    data object StartClicked : TrackingIntent
    data object StopClicked : TrackingIntent

    /** Sent after the permission dialog. The grants themselves are re-read from the platform. */
    data class PermissionResult(val canAskAgain: Boolean) : TrackingIntent

    data object IssueActionClicked : TrackingIntent
    data object IssueDismissed : TrackingIntent
    data object ScreenResumed : TrackingIntent

    data object ResetClicked : TrackingIntent
    data object ResetConfirmed : TrackingIntent
    data object ResetDismissed : TrackingIntent

    data class MarkerClicked(val pointId: Long) : TrackingIntent
    data object SelectionDismissed : TrackingIntent
    data object NoticeDismissed : TrackingIntent
}

/** One-off actions only the UI layer can perform. */
sealed interface TrackingEffect {
    data object RequestLocationPermissions : TrackingEffect
    data object OpenAppSettings : TrackingEffect
    data object OpenLocationSettings : TrackingEffect
}
