package com.erolgizlice.routetracker.core.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun `one degree of longitude on the equator is 2πR over 360`() {
        assertEquals(111_195.08, distanceMeters(0.0, 0.0, 0.0, 1.0), 0.5)
    }

    @Test
    fun `identical points are zero meters apart`() {
        assertEquals(0.0, distanceMeters(41.0082, 28.9784, 41.0082, 28.9784), 0.0)
    }

    @Test
    fun `distance is symmetric`() {
        val there = distanceMeters(41.0082, 28.9784, 41.0256, 29.0098)
        val back = distanceMeters(41.0256, 29.0098, 41.0082, 28.9784)
        assertEquals(there, back, 1e-9)
    }

    @Test
    fun `crossing the antimeridian is a short hop, not a trip around the world`() {
        // A naive degree difference would see 359.999° of longitude here.
        assertEquals(111.2, distanceMeters(0.0, 179.9995, 0.0, -179.9995), 0.1)
    }

    @Test
    fun `antipodal points do not produce NaN`() {
        val distance = distanceMeters(90.0, 0.0, -90.0, 0.0)
        assertFalse(distance.isNaN())
        assertEquals(Math.PI * EARTH_MEAN_RADIUS_METERS, distance, 1.0)
    }
}
