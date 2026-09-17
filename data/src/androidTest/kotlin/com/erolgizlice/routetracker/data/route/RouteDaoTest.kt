package com.erolgizlice.routetracker.data.route

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real SQL behind the anchor and the reset. The :core unit tests use a fake repository, so without
 * these tests `RouteDao.last()` could return the wrong row and every JVM test would still pass (D10).
 */
@RunWith(AndroidJUnit4::class)
class RouteDaoTest {

    private lateinit var database: RouteDatabase
    private lateinit var dao: RouteDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RouteDatabase::class.java)
            .build()
        dao = database.routeDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun lastReturnsTheMostRecentlyRecordedPoint() = runTest {
        val ids = listOf(1.0, 2.0, 3.0).map { dao.insert(point(latitude = it)) }

        val last = dao.last()

        assertEquals(ids.last(), last?.id)
        assertEquals(3.0, last!!.latitude, 0.0)
    }

    @Test
    fun lastIsNullForAnEmptyRoute() = runTest {
        assertNull(dao.last())
    }

    @Test
    fun routeIsOrderedByRecordingEvenWhenTheClockWentBackwards() = runTest {
        dao.insert(point(latitude = 1.0, recordedAt = 2_000))
        dao.insert(point(latitude = 2.0, recordedAt = 1_000)) // wall clock corrected backwards (D9)

        assertEquals(listOf(1.0, 2.0), dao.observeAll().first().map { it.latitude })
        assertEquals(2.0, dao.last()!!.latitude, 0.0)
    }

    @Test
    fun deleteAllEmptiesTheRouteSoTheNextFixStartsANewOne() = runTest {
        repeat(3) { dao.insert(point(latitude = it.toDouble())) }

        dao.deleteAll()

        assertEquals(emptyList<RoutePointEntity>(), dao.observeAll().first())
        assertNull(dao.last())
    }

    @Test
    fun addressUpdateForADeletedPointChangesNothingAndRecreatesNothing() = runTest {
        val id = dao.insert(point(latitude = 1.0))
        dao.deleteAll() // a reset lands between recording the point and resolving its address (D14)

        val changedRows = dao.updateAddress(id, "Somewhere")

        assertEquals(0, changedRows)
        assertNull(dao.last())
    }

    @Test
    fun addressUpdateForAnExistingPointIsStored() = runTest {
        val id = dao.insert(point(latitude = 1.0))

        assertEquals(1, dao.updateAddress(id, "Somewhere"))
        assertEquals("Somewhere", dao.last()?.address)
    }

    private fun point(latitude: Double, recordedAt: Long = 0) = RoutePointEntity(
        latitude = latitude,
        longitude = 29.0,
        recordedAtEpochMillis = recordedAt,
        address = null,
    )
}
