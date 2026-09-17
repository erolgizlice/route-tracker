package com.erolgizlice.routetracker.feature.tracking

/** How one route marker is drawn. One shared bitmap per style; see [rememberRouteMarkerIcons]. */
internal enum class RouteMarkerStyle { Start, Middle, End, Selected }

/**
 * The style of the marker for the point at [index] of a route of [pointCount] points.
 *
 * Selection wins over position, so the marker the details card describes is always the highlighted one.
 * A route of one point shows the start marker: it is where tracking began, and the end of a route that
 * has not gone anywhere yet says nothing.
 */
internal fun routeMarkerStyle(
    index: Int,
    pointCount: Int,
    pointId: Long,
    selectedPointId: Long?,
): RouteMarkerStyle = when {
    pointId == selectedPointId -> RouteMarkerStyle.Selected
    index == 0 -> RouteMarkerStyle.Start
    index == pointCount - 1 -> RouteMarkerStyle.End
    else -> RouteMarkerStyle.Middle
}
