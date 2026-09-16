package com.erolgizlice.routetracker.core.route

import com.erolgizlice.routetracker.core.route.RecordFixUseCase.Result
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Unlike [DistanceGateTest], which hands the gate an anchor, these tests feed fix sequences and check
 * that the anchor is chosen correctly: it moves only when a point is recorded.
 */
class RecordFixUseCaseTest {

    private val repository = FakeRouteRepository()
    private val recordFix = RecordFixUseCase(repository, DistanceGate())

    @Test
    fun `anchor advances only when a point is recorded`() = runTest {
        listOf(0.0, 60.0, 120.0, 150.0, 200.0, 230.0).forEach { recordFix(fixAt(north = it)) }

        // Anchoring on the previous fix would record only 0; anchoring on the first point would also record 150.
        assertEquals(listOf(0, 120, 230), recordedNorthMeters())
    }

    @Test
    fun `slow walk of 30 m steps records every fourth step`() = runTest {
        (0..300 step 30).forEach { recordFix(fixAt(north = it.toDouble())) }

        assertEquals(listOf(0, 120, 240), recordedNorthMeters())
    }

    @Test
    fun `stationary user with GPS jitter records only the start`() = runTest {
        val jitter = listOf(0.0 to 0.0, 40.0 to 40.0, -40.0 to 35.0, 45.0 to -30.0, -38.0 to -42.0, 0.0 to 48.0)

        jitter.forEach { (north, east) -> recordFix(fixAt(north, east, accuracy = 20f)) }

        assertEquals(1, repository.points.size)
    }

    @Test
    fun `inaccurate fix neither records nor moves the anchor`() = runTest {
        recordFix(fixAt(north = 0.0))
        recordFix(fixAt(north = 110.0, accuracy = 80f))
        recordFix(fixAt(north = 150.0))

        // Had the 110 m fix moved the anchor, 150 m would be only 40 m away and skipped.
        assertEquals(listOf(0, 150), recordedNorthMeters())
    }

    @Test
    fun `recording resumes from the persisted anchor after process death`() = runTest {
        val persisted = RoutePoint(
            id = 7,
            latitude = ORIGIN_LATITUDE,
            longitude = ORIGIN_LONGITUDE,
            recordedAtEpochMillis = 0,
            address = null,
        )
        val survivor = FakeRouteRepository(initial = listOf(persisted))
        // A new instance holds no state in memory; the anchor can only come from storage.
        val freshInstance = RecordFixUseCase(survivor, DistanceGate())

        freshInstance(fixAt(north = 50.0))
        freshInstance(fixAt(north = 101.0))

        assertEquals(listOf(0, 101), survivor.points.map { it.metersNorthOfOrigin().roundToInt() })
    }

    @Test
    fun `after a reset the next accurate fix starts a new route`() = runTest {
        recordFix(fixAt(north = 0.0))
        recordFix(fixAt(north = 150.0))
        repository.reset()

        recordFix(fixAt(north = 160.0))

        assertEquals(listOf(160), recordedNorthMeters())
    }

    @Test
    fun `concurrent fixes cannot both pass against the same anchor`() = runTest {
        // Two fixes 10 m apart delivered together, e.g. a batched LocationResult.
        val jobs = listOf(0.0, 10.0).map { north -> launch { recordFix(fixAt(north = north)) } }
        jobs.joinAll()

        assertEquals(1, repository.points.size)
    }

    @Test
    fun `recorded point keeps the fix's own timestamp`() = runTest {
        val result = recordFix(fixAt(north = 0.0, timestamp = 1_726_500_000_000))

        assertEquals(1_726_500_000_000, (result as Result.Recorded).point.recordedAtEpochMillis)
    }

    @Test
    fun `skipped result carries the gate's reason`() = runTest {
        recordFix(fixAt(north = 0.0))

        val result = recordFix(fixAt(north = 40.0))

        val decision = (result as Result.Skipped).decision
        assertTrue(decision is DistanceGate.Decision.RejectTooClose)
        assertEquals(40.0, (decision as DistanceGate.Decision.RejectTooClose).distanceMeters, 0.01)
    }

    private fun recordedNorthMeters() = repository.points.map { it.metersNorthOfOrigin().roundToInt() }
}
