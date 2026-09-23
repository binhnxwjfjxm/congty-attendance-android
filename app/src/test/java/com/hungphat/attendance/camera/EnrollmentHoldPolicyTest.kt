package com.hungphat.attendance.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentHoldPolicyTest {
    @Test
    fun requiresAtLeastOneAndHalfSecondsOfStablePose() {
        val startedAt = 10_000L

        assertFalse(EnrollmentHoldPolicy.isReady(startedAt, startedAt + 1_499L))
        assertEquals(1L, EnrollmentHoldPolicy.remainingMillis(startedAt, startedAt + 1_499L))
        assertTrue(EnrollmentHoldPolicy.isReady(startedAt, startedAt + 1_500L))
    }

    @Test
    fun invalidStartDoesNotBecomeReady() {
        assertFalse(EnrollmentHoldPolicy.isReady(0L, 5_000L))
        assertEquals(
            EnrollmentHoldPolicy.MIN_STABLE_HOLD_MILLIS,
            EnrollmentHoldPolicy.remainingMillis(0L, 5_000L),
        )
    }
}
