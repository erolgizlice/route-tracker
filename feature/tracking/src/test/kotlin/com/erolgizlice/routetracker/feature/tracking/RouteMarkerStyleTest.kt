package com.erolgizlice.routetracker.feature.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteMarkerStyleTest {

    @Test
    fun `the first point of a route is the start`() {
        assertEquals(RouteMarkerStyle.Start, style(index = 0, pointCount = 7))
    }

    @Test
    fun `the last point of a route is the end`() {
        assertEquals(RouteMarkerStyle.End, style(index = 6, pointCount = 7))
    }

    @Test
    fun `the points in between are middle points`() {
        val styles = (1..5).map { style(index = it, pointCount = 7) }
        assertEquals(List(5) { RouteMarkerStyle.Middle }, styles)
    }

    @Test
    fun `the only point of a route is the start, not the end`() {
        assertEquals(RouteMarkerStyle.Start, style(index = 0, pointCount = 1))
    }

    @Test
    fun `the selected point is highlighted even when it is the start`() {
        assertEquals(RouteMarkerStyle.Selected, style(index = 0, pointCount = 7, selectedPointId = ID))
    }

    @Test
    fun `the selected point is highlighted even when it is the end`() {
        assertEquals(RouteMarkerStyle.Selected, style(index = 6, pointCount = 7, selectedPointId = ID))
    }

    @Test
    fun `selecting one point does not highlight the others`() {
        assertEquals(RouteMarkerStyle.Middle, style(index = 3, pointCount = 7, selectedPointId = ID + 1))
    }

    private companion object {
        const val ID = 42L

        fun style(index: Int, pointCount: Int, selectedPointId: Long? = null) =
            routeMarkerStyle(index = index, pointCount = pointCount, pointId = ID, selectedPointId = selectedPointId)
    }
}
