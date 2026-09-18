package com.erolgizlice.routetracker.feature.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen's transitions, on the JVM. Everything the transitions need from Android arrives as a
 * [LocationSnapshot], so these are ordinary function calls: state in, state out.
 */
class TrackingReducerTest {

    private val precise = LocationSnapshot(hasPrecise = true, hasAny = true, isEnabled = true)
    private val approximate = LocationSnapshot(hasPrecise = false, hasAny = true, isEnabled = true)
    private val noPermission = LocationSnapshot(hasPrecise = false, hasAny = false, isEnabled = true)
    private val locationOff = LocationSnapshot(hasPrecise = true, hasAny = true, isEnabled = false)

    @Test
    fun `pressing Start with precise permission clears the issue`() {
        val state = ScreenState(issue = TrackingIssue.PreciseLocationDenied)

        assertNull(state.reduce(TrackingIntent.StartClicked, precise).issue)
    }

    @Test
    fun `pressing Start without precise permission leaves the state alone`() {
        val state = ScreenState(issue = TrackingIssue.LocationDenied, selectedPointId = 7L)

        // The dialog answers this press; nothing is decided until its PermissionResult arrives.
        assertSame(state, state.reduce(TrackingIntent.StartClicked, approximate))
        assertSame(state, state.reduce(TrackingIntent.StartClicked, noPermission))
    }

    @Test
    fun `granting precise after the dialog clears the issue and records the permission`() {
        val started = ScreenState().reduce(TrackingIntent.StartClicked, noPermission)

        val granted = started.reduce(TrackingIntent.PermissionResult(canAskAgain = true), precise)

        assertNull(granted.issue)
        assertTrue(granted.hasLocationPermission)
    }

    @Test
    fun `granting approximate only asks for precise`() {
        val state = ScreenState().reduce(TrackingIntent.PermissionResult(canAskAgain = true), approximate)

        assertEquals(TrackingIssue.PreciseLocationDenied, state.issue)
        assertTrue(state.hasLocationPermission)
    }

    @Test
    fun `approximate with no second chance is still only denied until the upgrade was asked for`() {
        // Android 16 shows the upgrade dialog once even though the rationale is already false (D18).
        val state = ScreenState().reduce(TrackingIntent.PermissionResult(canAskAgain = false), approximate)

        assertEquals(TrackingIssue.PreciseLocationDenied, state.issue)
    }

    @Test
    fun `approximate with no second chance is blocked once the upgrade has been asked for`() {
        val asked = ScreenState().reduce(TrackingIntent.PermissionResult(canAskAgain = true), approximate)

        val again = asked.reduce(TrackingIntent.PermissionResult(canAskAgain = false), approximate)

        assertEquals(TrackingIssue.PreciseLocationBlocked, again.issue)
    }

    @Test
    fun `pressing Start while blocked leaves it blocked`() {
        val blocked = ScreenState(issue = TrackingIssue.PreciseLocationBlocked)

        val again = blocked.reduce(TrackingIntent.PermissionResult(canAskAgain = false), approximate)

        assertEquals(TrackingIssue.PreciseLocationBlocked, again.issue)
    }

    @Test
    fun `refusing location outright is denied while the dialog can come back, and blocked after that`() {
        val denied = ScreenState().reduce(TrackingIntent.PermissionResult(canAskAgain = true), noPermission)
        val blocked = ScreenState().reduce(TrackingIntent.PermissionResult(canAskAgain = false), noPermission)

        assertEquals(TrackingIssue.LocationDenied, denied.issue)
        assertEquals(TrackingIssue.LocationBlocked, blocked.issue)
    }

    @Test
    fun `the device-wide switch being off is its own issue, both on Start and after the dialog`() {
        assertEquals(
            TrackingIssue.LocationDisabled,
            ScreenState().reduce(TrackingIntent.StartClicked, locationOff).issue,
        )
        assertEquals(
            TrackingIssue.LocationDisabled,
            ScreenState().reduce(TrackingIntent.PermissionResult(canAskAgain = true), locationOff).issue,
        )
    }

    @Test
    fun `coming back to the screen clears an issue the user has fixed`() {
        val disabled = ScreenState(issue = TrackingIssue.LocationDisabled)
        val blocked = ScreenState(issue = TrackingIssue.PreciseLocationBlocked)

        assertNull(disabled.reduce(TrackingIntent.ScreenResumed, precise).issue)
        assertNull(blocked.reduce(TrackingIntent.ScreenResumed, precise).issue)
    }

    @Test
    fun `coming back to the screen keeps an issue the user has not fixed`() {
        val disabled = ScreenState(issue = TrackingIssue.LocationDisabled)
        val blocked = ScreenState(issue = TrackingIssue.PreciseLocationBlocked)

        // The switch is what LocationDisabled is about; the permission is what the others are about.
        assertEquals(TrackingIssue.LocationDisabled, disabled.reduce(TrackingIntent.ScreenResumed, locationOff).issue)
        assertEquals(TrackingIssue.PreciseLocationBlocked, blocked.reduce(TrackingIntent.ScreenResumed, approximate).issue)
    }

    @Test
    fun `coming back to the screen re-reads the permission for the my-location layer`() {
        val state = ScreenState(hasLocationPermission = false).reduce(TrackingIntent.ScreenResumed, approximate)

        assertTrue(state.hasLocationPermission)
        assertNull(state.issue)
    }

    @Test
    fun `dismissing an issue hides it`() {
        val state = ScreenState(issue = TrackingIssue.LocationDisabled)

        assertNull(state.reduce(TrackingIntent.IssueDismissed, precise).issue)
    }

    @Test
    fun `Reset asks first, and confirming or dismissing closes the question`() {
        val asking = ScreenState().reduce(TrackingIntent.ResetClicked, precise)
        assertTrue(asking.isResetConfirmationVisible)

        assertFalse(asking.reduce(TrackingIntent.ResetConfirmed, precise).isResetConfirmationVisible)
        assertFalse(asking.reduce(TrackingIntent.ResetDismissed, precise).isResetConfirmationVisible)
    }

    @Test
    fun `tapping a marker selects it and starts its address from scratch`() {
        val stale = ScreenState(selectedPointId = 1L, addressStatus = AddressStatus.Unavailable)

        val selected = stale.reduce(TrackingIntent.MarkerClicked(pointId = 2L), precise)

        assertEquals(2L, selected.selectedPointId)
        assertEquals(AddressStatus.Idle, selected.addressStatus)
    }

    @Test
    fun `dismissing the card clears the selection and the address status`() {
        val selected = ScreenState(selectedPointId = 2L, addressStatus = AddressStatus.Resolving)

        val dismissed = selected.reduce(TrackingIntent.SelectionDismissed, precise)

        assertNull(dismissed.selectedPointId)
        assertEquals(AddressStatus.Idle, dismissed.addressStatus)
    }

    @Test
    fun `dismissing the notice hides it`() {
        val state = ScreenState(notice = TrackingNotice.SessionEndedWhileClosed)

        assertNull(state.reduce(TrackingIntent.NoticeDismissed, precise).notice)
    }

    @Test
    fun `the intents that are only effects do not move the state`() {
        val state = ScreenState(selectedPointId = 3L, issue = TrackingIssue.LocationBlocked)

        assertSame(state, state.reduce(TrackingIntent.StopClicked, precise))
        assertSame(state, state.reduce(TrackingIntent.IssueActionClicked, precise))
        assertSame(state, state.reduce(TrackingIntent.RetryAddressClicked, precise))
    }
}
