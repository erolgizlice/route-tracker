package com.erolgizlice.routetracker.feature.tracking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/** A marker bitmap, the spot in it that sits on the point, and its drawing order. */
internal data class RouteMarkerIcon(val descriptor: BitmapDescriptor, val anchor: Offset, val zIndex: Float)

/**
 * One bitmap per [RouteMarkerStyle], drawn once and shared by every marker that uses it: a route of a
 * thousand points holds four bitmaps, not a thousand.
 *
 * Call this from inside the map's content. `BitmapDescriptorFactory` throws
 * "IBitmapDescriptorFactory is not initialized" until the Maps SDK has started, and the content of a
 * `GoogleMap` is composed after that.
 */
@Composable
internal fun rememberRouteMarkerIcons(): Map<RouteMarkerStyle, RouteMarkerIcon> {
    val density = LocalDensity.current
    return remember(density) { RouteMarkerStyle.entries.associateWith { it.icon(density) } }
}

// The route is teal, like the launcher icon; the newest point stands out; the open card's point is blue.
private val RouteColor = Color(0xFF0F766E)
private val LatestColor = Color(0xFFB91C1C)
private val SelectedColor = Color(0xFF1D4ED8)

private val PinWidth = 34.dp
private val PinHeight = 46.dp
private val SelectedPinWidth = 40.dp
private val SelectedPinHeight = 54.dp
private val DotSize = 30.dp

/** Transparent space left around a bitmap: it is part of the tap target, which a bare 21 dp dot is not. */
private val Padding = 3.dp
private val TipInset = 4.dp

private fun RouteMarkerStyle.icon(density: Density): RouteMarkerIcon = when (this) {
    RouteMarkerStyle.Middle -> dotIcon(density, RouteColor, zIndex = 1f)
    RouteMarkerStyle.Start -> pinIcon(density, RouteColor, PinWidth, PinHeight, outline = 2.5.dp, zIndex = 2f)
    RouteMarkerStyle.End -> pinIcon(density, LatestColor, PinWidth, PinHeight, outline = 2.5.dp, zIndex = 3f)
    RouteMarkerStyle.Selected ->
        pinIcon(density, SelectedColor, SelectedPinWidth, SelectedPinHeight, outline = 3.dp, zIndex = 4f)
}

/** A pin whose tip sits on the point, so the head stays clear of the my-location dot underneath it. */
private fun pinIcon(
    density: Density,
    color: Color,
    width: Dp,
    height: Dp,
    outline: Dp,
    zIndex: Float,
): RouteMarkerIcon {
    val descriptor = descriptor(density, width, height) {
        val outlinePx = outline.toPx()
        val radius = size.width / 2f - outlinePx - Padding.toPx()
        val head = Offset(size.width / 2f, Padding.toPx() + outlinePx + radius)
        val tipY = size.height - TipInset.toPx()
        drawPin(Color.White, head, radius + outlinePx, tipY)
        drawPin(color, head, radius, tipY - outlinePx * 1.8f)
        drawCircle(Color.White, radius = radius * 0.36f, center = head)
    }
    return RouteMarkerIcon(descriptor, Offset(0.5f, (height - TipInset) / height), zIndex)
}

/** A dot centred on the point: small enough that neighbours 100 m apart never touch. */
private fun dotIcon(density: Density, color: Color, zIndex: Float): RouteMarkerIcon {
    val descriptor = descriptor(density, DotSize, DotSize) {
        val radius = 8.dp.toPx()
        drawCircle(Color.White, radius = radius + 2.5.dp.toPx(), center = size.center)
        drawCircle(color, radius = radius, center = size.center)
    }
    return RouteMarkerIcon(descriptor, Offset(0.5f, 0.5f), zIndex)
}

private fun DrawScope.drawPin(color: Color, head: Offset, radius: Float, tipY: Float) {
    drawCircle(color, radius = radius, center = head)
    drawPath(
        Path().apply {
            moveTo(head.x, tipY)
            lineTo(head.x - radius * 0.62f, head.y + radius * 0.78f)
            lineTo(head.x + radius * 0.62f, head.y + radius * 0.78f)
            close()
        },
        color,
    )
}

private fun descriptor(density: Density, width: Dp, height: Dp, draw: DrawScope.() -> Unit): BitmapDescriptor {
    val widthPx = with(density) { width.roundToPx() }
    val heightPx = with(density) { height.roundToPx() }
    val bitmap = ImageBitmap(widthPx, heightPx)
    val size = Size(widthPx.toFloat(), heightPx.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) { draw() }
    return BitmapDescriptorFactory.fromBitmap(bitmap.asAndroidBitmap())
}
