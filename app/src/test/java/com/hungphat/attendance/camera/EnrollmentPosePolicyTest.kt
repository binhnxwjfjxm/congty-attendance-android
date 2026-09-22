package com.hungphat.attendance.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentPosePolicyTest {
    private fun frame(
        yaw: Float = 0f,
        roll: Float = 0f,
        ratio: Float = 0.35f,
        count: Int = 1,
    ) = FaceFrame(
        faceCount = count,
        faceWidthRatio = ratio,
        yawDegrees = yaw,
        rollDegrees = roll,
    )

    @Test
    fun front_requires_centered_face() {
        assertTrue(EnrollmentPosePolicy.matches(EnrollmentPose.FRONT, frame(yaw = 6f)))
        assertFalse(EnrollmentPosePolicy.matches(EnrollmentPose.FRONT, frame(yaw = 18f)))
    }

    @Test
    fun left_and_right_require_distinct_yaw_ranges() {
        assertTrue(EnrollmentPosePolicy.matches(EnrollmentPose.TURN_LEFT, frame(yaw = -20f)))
        assertFalse(EnrollmentPosePolicy.matches(EnrollmentPose.TURN_LEFT, frame(yaw = 20f)))
        assertTrue(EnrollmentPosePolicy.matches(EnrollmentPose.TURN_RIGHT, frame(yaw = 20f)))
        assertFalse(EnrollmentPosePolicy.matches(EnrollmentPose.TURN_RIGHT, frame(yaw = -20f)))
    }

    @Test
    fun pose_rejects_small_tilted_or_multiple_faces() {
        assertFalse(EnrollmentPosePolicy.matches(EnrollmentPose.FRONT, frame(ratio = 0.2f)))
        assertFalse(EnrollmentPosePolicy.matches(EnrollmentPose.FRONT, frame(roll = 22f)))
        assertFalse(EnrollmentPosePolicy.matches(EnrollmentPose.FRONT, frame(count = 2)))
    }
}
