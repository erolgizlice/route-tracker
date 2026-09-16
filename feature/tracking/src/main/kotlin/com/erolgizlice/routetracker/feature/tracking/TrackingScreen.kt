package com.erolgizlice.routetracker.feature.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erolgizlice.routetracker.core.route.RoutePoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DefaultCameraTarget = LatLng(41.0082, 28.9784) // Istanbul, until a route exists
private const val DefaultZoom = 11f
private const val RouteZoom = 16f

@Composable
fun TrackingRoute(
    hasMapsApiKey: Boolean,
    viewModel: TrackingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrackingScreen(state = state, hasMapsApiKey = hasMapsApiKey, onIntent = viewModel::onIntent)
}

@Composable
internal fun TrackingScreen(
    state: TrackingState,
    hasMapsApiKey: Boolean,
    onIntent: (TrackingIntent) -> Unit,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultCameraTarget, DefaultZoom)
    }
    // Saveable: a rotation must not yank the camera back after the user has panned away.
    var hasCenteredOnRoute by rememberSaveable { mutableStateOf(false) }
    val hasRoute = state.points.isNotEmpty()

    // Keyed on route presence, not only on loading: on a fresh install the route is still empty when
    // loading finishes, and the first recorded point must center the camera when it arrives later.
    LaunchedEffect(state.isLoading, hasRoute) {
        when {
            state.isLoading -> Unit
            !hasRoute -> hasCenteredOnRoute = false // after a reset, center on the next route again
            !hasCenteredOnRoute -> {
                hasCenteredOnRoute = true
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(state.points.last().latLng, RouteZoom))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
            onMapClick = { onIntent(TrackingIntent.SelectionDismissed) },
        ) {
            if (state.points.size > 1) {
                Polyline(points = state.points.map { it.latLng }, width = 8f)
            }
            state.points.forEach { point ->
                key(point.id) {
                    Marker(
                        state = rememberUpdatedMarkerState(position = point.latLng),
                        onClick = {
                            onIntent(TrackingIntent.MarkerClicked(point.id))
                            true // consume: details are shown in our own card, not the info window
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            if (!hasMapsApiKey) MissingMapsKeyCard()
        }

        state.selectedPoint?.let { point ->
            PointDetailsCard(
                point = point,
                onDismiss = { onIntent(TrackingIntent.SelectionDismissed) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun MissingMapsKeyCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.maps_key_missing_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.maps_key_missing_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PointDetailsCard(point: RoutePoint, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = point.address ?: stringResource(R.string.point_address_unavailable),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.point_recorded_at, point.formattedRecordedAt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

private val RoutePoint.latLng get() = LatLng(latitude, longitude)

private fun RoutePoint.formattedRecordedAt(): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(recordedAtEpochMillis))
