package com.erolgizlice.routetracker.core.route

import com.erolgizlice.routetracker.core.route.DistanceGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceGateTest {

    private val gate = DistanceGate()

    private val origin = RoutePoint(
        id = 1,
        latitude = ORIGIN_LATITUDE,
        longitude = ORIGIN_LONGITUDE,
        recordedAtEpochMillis = 0,
        address = null,
    )

    @Test
    fun `first accurate fix starts the route`() {
        assertEquals(Decision.Record, gate.evaluate(anchor = null, fix = fixAt(north = 0.0, east = 0.0)))
    }

    @Test
    fun `inaccurate first fix cannot become the anchor`() {
        val coarseNetworkFix = fixAt(north = 0.0, east = 0.0, accuracy = 600f)
        assertEquals(Decision.RejectInaccurate, gate.evaluate(anchor = null, fix = coarseNetworkFix))
    }

    @Test
    fun `fix just under 100 m from the anchor is rejected`() {
        val decision = gate.evaluate(origin, fixAt(north = 99.5, east = 0.0))
        assertTrue(decision is Decision.RejectTooClose)
        assertEquals(99.5, (decision as Decision.RejectTooClose).distanceMeters, 0.01)
    }

    @Test
    fun `fix just over 100 m from the anchor is recorded`() {
        assertEquals(Decision.Record, gate.evaluate(origin, fixAt(north = 100.5, east = 0.0)))
    }

    @Test
    fun `distance counts in any direction, not only along one axis`() {
        // 71 m north + 71 m east is ~100.4 m diagonally, although neither axis reaches 100 m.
        assertEquals(Decision.Record, gate.evaluate(origin, fixAt(north = 71.0, east = 71.0)))
    }

    @Test
    fun `far but inaccurate fix is rejected`() {
        assertEquals(Decision.RejectInaccurate, gate.evaluate(origin, fixAt(north = 500.0, east = 0.0, accuracy = 51f)))
    }

    @Test
    fun `accuracy exactly at the limit is accepted`() {
        assertEquals(Decision.Record, gate.evaluate(origin, fixAt(north = 150.0, east = 0.0, accuracy = 50f)))
    }

    @Test
    fun `fix without an accuracy estimate is rejected`() {
        assertEquals(Decision.RejectInaccurate, gate.evaluate(origin, fixAt(north = 150.0, east = 0.0, accuracy = null)))
    }

    @Test
    fun `NaN accuracy is rejected`() {
        assertEquals(Decision.RejectInaccurate, gate.evaluate(origin, fixAt(north = 150.0, east = 0.0, accuracy = Float.NaN)))
    }

    @Test
    fun `slow walk records once the anchor is 100 m behind, even though every step is 30 m`() {
        // Measured fix-to-fix, none of these 30 m steps would ever reach the threshold.
        val steps = listOf(30.0, 60.0, 90.0, 120.0).map { gate.evaluate(origin, fixAt(north = it, east = 0.0)) }

        assertTrue(steps.take(3).all { it is Decision.RejectTooClose })
        assertEquals(Decision.Record, steps.last())
    }

    @Test
    fun `GPS jitter around a stationary user never records`() {
        val jitter = listOf(40.0 to 40.0, -40.0 to 35.0, 45.0 to -30.0, -38.0 to -42.0, 0.0 to 48.0, 49.0 to 0.0)

        val decisions = jitter.map { (north, east) -> gate.evaluate(origin, fixAt(north, east, accuracy = 20f)) }

        assertTrue(decisions.all { it is Decision.RejectTooClose })
    }
}
