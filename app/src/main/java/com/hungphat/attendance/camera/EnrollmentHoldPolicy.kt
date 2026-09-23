package com.hungphat.attendance.camera

object EnrollmentHoldPolicy {
    const val MIN_STABLE_HOLD_MILLIS = 1_500L

    fun remainingMillis(stableSinceMillis: Long, nowMillis: Long): Long {
        if (stableSinceMillis <= 0L || nowMillis < stableSinceMillis) {
            return MIN_STABLE_HOLD_MILLIS
        }
        return (MIN_STABLE_HOLD_MILLIS - (nowMillis - stableSinceMillis)).coerceAtLeast(0L)
    }

    fun isReady(stableSinceMillis: Long, nowMillis: Long): Boolean =
        remainingMillis(stableSinceMillis, nowMillis) == 0L
}
