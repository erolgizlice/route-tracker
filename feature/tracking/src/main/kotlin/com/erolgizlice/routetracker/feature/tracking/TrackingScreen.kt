package com.erolgizlice.routetracker.feature.tracking

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.erolgizlice.routetracker.core.route.RoutePoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import java.util.Locale

private val DefaultCameraTarget = LatLng(41.0082, 28.9784) // Istanbul, until a route exists
private const val DefaultZoom = 11f
private const val RouteZoom = 16f

/**
 * How long the placeholder may cover the map. A cap, not the normal path: offline with an empty tile
 * cache the map never reports itself loaded and draws nothing at all, so the placeholder would cover an
 * empty screen, and the Google logo, for good (D24).
 */
private const val MapPlaceholderTimeoutMillis = 3_000L

@Composable
fun TrackingRoute(
    hasMapsApiKey: Boolean,
    viewModel: TrackingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // The launcher survives recreation: a rotation while the dialog is open still delivers the result.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Only the Activity can tell whether the dialog may be shown again; the ViewModel re-reads the grants.
        val canAskAgain = activity == null ||
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.onIntent(TrackingIntent.PermissionResult(canAskAgain))
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    TrackingEffect.RequestLocationPermissions -> permissionLauncher.launch(trackingPermissions())
                    TrackingEffect.OpenAppSettings -> context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                    TrackingEffect.OpenLocationSettings -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
        }
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(TrackingIntent.ScreenResumed)
        onPauseOrDispose { }
    }

    TrackingScreen(state = state, hasMapsApiKey = hasMapsApiKey, onIntent = viewModel::onIntent)
}

/** Notifications are optional: when denied, the foreground notification is hidden but tracking still runs. */
private fun trackingPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
internal fun TrackingScreen(
    state: TrackingState,
    hasMapsApiKey: Boolean,
    onIntent: (TrackingIntent) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    // The bottom overlay is measured so the map keeps the Google logo, which must stay visible, above it.
    var bottomOverlayHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = Modifier.fillMaxSize()) {
        // The map waits for the stored route, so that its camera can open on the route instead of
        // opening on a default view of the city and moving a moment later (D24).
        if (!state.isLoading) {
            RouteMap(
                state = state,
                onIntent = onIntent,
                contentPadding = PaddingValues(
                    start = safeDrawing.calculateStartPadding(layoutDirection),
                    top = safeDrawing.calculateTopPadding(),
                    end = safeDrawing.calculateEndPadding(layoutDirection),
                    bottom = maxOf(safeDrawing.calculateBottomPadding(), bottomOverlayHeight),
                ),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            if (!hasMapsApiKey) MissingMapsKeyCard()
            state.issue?.let { issue ->
                IssueCard(
                    issue = issue,
                    onAction = { onIntent(TrackingIntent.IssueActionClicked) },
                    onDismiss = { onIntent(TrackingIntent.IssueDismissed) },
                )
            }
            state.notice?.let { notice ->
                NoticeCard(notice = notice, onDismiss = { onIntent(TrackingIntent.NoticeDismissed) })
            }
        }

        val bottomModifier = Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged { size -> bottomOverlayHeight = with(density) { size.height.toDp() } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
        val selectedPoint = state.selectedPoint
        if (selectedPoint != null) {
            PointDetailsCard(
                point = selectedPoint,
                addressStatus = state.addressStatus,
                onRetry = { onIntent(TrackingIntent.RetryAddressClicked) },
                onDismiss = { onIntent(TrackingIntent.SelectionDismissed) },
                modifier = bottomModifier,
            )
        } else {
            ControlBar(state = state, onIntent = onIntent, modifier = bottomModifier)
        }
    }

    if (state.isResetConfirmationVisible) {
        ResetRouteDialog(
            markerCount = state.points.size,
            onConfirm = { onIntent(TrackingIntent.ResetConfirmed) },
            onDismiss = { onIntent(TrackingIntent.ResetDismissed) },
        )
    }
}

/**
 * The map, and the route drawn on it.
 *
 * Composed only once the stored route is known: the camera then opens where the route is, instead of
 * opening on a default view and moving there afterwards (D24).
 */
@Composable
private fun RouteMap(
    state: TrackingState,
    onIntent: (TrackingIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    val points = state.points
    val resources = LocalContext.current.resources
    val cameraPositionState = rememberCameraPositionState {
        position = points.lastOrNull()
            ?.let { CameraPosition.fromLatLngZoom(it.latLng, RouteZoom) }
            ?: CameraPosition.fromLatLngZoom(DefaultCameraTarget, DefaultZoom)
    }
    // Saveable: a rotation must not yank the camera back after the user has panned away. A route the
    // camera opened on is already centered.
    var hasCenteredOnRoute by rememberSaveable { mutableStateOf(points.isNotEmpty()) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var placeholderTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MapPlaceholderTimeoutMillis)
        placeholderTimedOut = true
    }
    // What "started" means for this screen: the route is on screen and the map has finished rendering.
    // Without this, `am start -W` and startup profilers stop at the first frame, which is an empty map.
    ReportDrawnWhen { isMapLoaded }

    // On a fresh install, and after a reset, the route is empty here and its first point arrives later.
    LaunchedEffect(points.isNotEmpty()) {
        when {
            points.isEmpty() -> hasCenteredOnRoute = false // after a reset, center on the next route again
            !hasCenteredOnRoute -> {
                hasCenteredOnRoute = true
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(points.last().latLng, RouteZoom))
            }
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        // The my-location layer throws a SecurityException without a location permission.
        properties = MapProperties(isMyLocationEnabled = state.hasLocationPermission),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = state.hasLocationPermission),
        contentPadding = contentPadding,
        onMapClick = { onIntent(TrackingIntent.SelectionDismissed) },
        onMapLoaded = { isMapLoaded = true },
    ) {
        // Inside the map's content, where the Maps SDK has already started: see rememberRouteMarkerIcons.
        val icons = rememberRouteMarkerIcons()
        if (points.size > 1) {
            Polyline(points = points.map { it.latLng }, width = 8f)
        }
        points.forEachIndexed { index, point ->
            key(point.id) {
                val icon = icons.getValue(
                    routeMarkerStyle(
                        index = index,
                        pointCount = points.size,
                        pointId = point.id,
                        selectedPointId = state.selectedPoint?.id,
                    ),
                )
                Marker(
                    state = rememberUpdatedMarkerState(position = point.latLng),
                    icon = icon.descriptor,
                    anchor = icon.anchor,
                    zIndex = icon.zIndex,
                    contentDescription = resources.getString(R.string.marker_content_description, index + 1),
                    onClick = {
                        onIntent(TrackingIntent.MarkerClicked(point.id))
                        true // consume: details are shown in our own card, not the info window
                    },
                )
            }
        }
    }

    // An unloaded map draws an empty grid, which reads as broken, where the app's own background reads
    // as opening. This changes what the wait looks like, not how long it is.
    val placeholderAlpha by animateFloatAsState(
        targetValue = if (isMapLoaded || placeholderTimedOut) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "map placeholder",
    )
    if (placeholderAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = placeholderAlpha }
                .background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun ControlBar(state: TrackingState, onIntent: (TrackingIntent) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (state.isTracking) R.string.status_tracking else R.string.status_not_tracking),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = pluralStringResource(R.plurals.marker_count, state.points.size, state.points.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(
                onClick = { onIntent(TrackingIntent.ResetClicked) },
                enabled = state.points.isNotEmpty(),
            ) {
                Text(stringResource(R.string.action_reset_route))
            }
            if (state.isTracking) {
                Button(
                    onClick = { onIntent(TrackingIntent.StopClicked) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.action_stop_tracking))
                }
            } else {
                Button(onClick = { onIntent(TrackingIntent.StartClicked) }) {
                    Text(stringResource(R.string.action_start_tracking))
                }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: TrackingIssue, onAction: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = when (issue) {
        TrackingIssue.LocationDenied -> R.string.issue_location_title to R.string.issue_location_denied_body
        TrackingIssue.LocationBlocked -> R.string.issue_location_title to R.string.issue_location_blocked_body
        TrackingIssue.PreciseLocationDenied -> R.string.issue_precise_title to R.string.issue_precise_denied_body
        TrackingIssue.PreciseLocationBlocked -> R.string.issue_precise_title to R.string.issue_precise_blocked_body
        TrackingIssue.LocationDisabled -> R.string.issue_location_disabled_title to R.string.issue_location_disabled_body
    }
    val actionLabel = when (issue.action) {
        IssueAction.RequestPermission -> R.string.issue_action_allow
        IssueAction.OpenAppSettings, IssueAction.OpenLocationSettings -> R.string.issue_action_open_settings
    }
    MessageCard(
        title = stringResource(title),
        body = stringResource(body),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
            TextButton(onClick = onAction) { Text(stringResource(actionLabel)) }
        },
    )
}

@Composable
private fun NoticeCard(notice: TrackingNotice, onDismiss: () -> Unit) {
    val body = when (notice) {
        TrackingNotice.SessionEndedWhileClosed -> R.string.notice_session_ended
    }
    MessageCard(
        title = null,
        body = stringResource(body),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) } },
    )
}

@Composable
private fun MissingMapsKeyCard() {
    MessageCard(
        title = stringResource(R.string.maps_key_missing_title),
        body = stringResource(R.string.maps_key_missing_body),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        actions = null,
    )
}

@Composable
private fun MessageCard(
    title: String?,
    body: String,
    containerColor: Color,
    actions: (@Composable () -> Unit)?,
) {
    Card(colors = CardDefaults.cardColors(containerColor = containerColor), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (actions != null) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { actions() }
            }
        }
    }
}

@Composable
private fun ResetRouteDialog(markerCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_route_title)) },
        text = { Text(pluralStringResource(R.plurals.reset_route_body, markerCount, markerCount)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_reset_route)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PointDetailsCard(
    point: RoutePoint,
    addressStatus: AddressStatus,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUnavailable = point.address == null && addressStatus == AddressStatus.Unavailable
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = point.address ?: stringResource(
                    if (isUnavailable) R.string.address_unavailable_title else R.string.address_looking_up,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (isUnavailable) {
                Text(stringResource(R.string.address_unavailable_body), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.point_recorded_at, point.formattedRecordedAt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (isUnavailable) TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}

private val RoutePoint.latLng get() = LatLng(latitude, longitude)

private fun RoutePoint.formattedRecordedAt(): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(recordedAtEpochMillis))
