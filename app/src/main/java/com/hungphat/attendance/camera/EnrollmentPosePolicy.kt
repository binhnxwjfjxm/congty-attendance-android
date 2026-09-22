package com.hungphat.attendance.camera

import kotlin.math.abs

enum class EnrollmentPose {
    FRONT,
    TURN_LEFT,
    TURN_RIGHT,
}

object EnrollmentPosePolicy {
    private const val MIN_FACE_WIDTH_RATIO = 0.28f
    private const val MAX_ROLL_DEGREES = 15f
    private const val FRONT_MAX_YAW = 12f
    private const val TURN_MIN_YAW = 14f
    private const val TURN_MAX_YAW = 35f

    fun matches(pose: EnrollmentPose, frame: FaceFrame): Boolean {
        if (frame.faceCount != 1) return false
        if (frame.faceWidthRatio < MIN_FACE_WIDTH_RATIO) return false
        if (abs(frame.rollDegrees) > MAX_ROLL_DEGREES) return false

        return when (pose) {
            EnrollmentPose.FRONT -> abs(frame.yawDegrees) <= FRONT_MAX_YAW
            EnrollmentPose.TURN_LEFT -> frame.yawDegrees <= -TURN_MIN_YAW && frame.yawDegrees >= -TURN_MAX_YAW
            EnrollmentPose.TURN_RIGHT -> frame.yawDegrees >= TURN_MIN_YAW && frame.yawDegrees <= TURN_MAX_YAW
        }
    }
}
