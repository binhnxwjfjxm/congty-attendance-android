package com.hungphat.attendance.camera

import kotlin.math.abs

enum class FaceGuidance {
    NO_FACE,
    MULTIPLE_FACES,
    MOVE_CLOSER,
    LOOK_STRAIGHT,
    READY_CANDIDATE,
}

data class FaceFrame(
    val faceCount: Int,
    val faceWidthRatio: Float = 0f,
    val yawDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
)

object FaceReadinessPolicy {
    private const val MIN_FACE_WIDTH_RATIO = 0.28f
    private const val MAX_YAW_DEGREES = 18f
    private const val MAX_ROLL_DEGREES = 15f

    fun evaluate(frame: FaceFrame): FaceGuidance = when {
        frame.faceCount == 0 -> FaceGuidance.NO_FACE
        frame.faceCount > 1 -> FaceGuidance.MULTIPLE_FACES
        frame.faceWidthRatio < MIN_FACE_WIDTH_RATIO -> FaceGuidance.MOVE_CLOSER
        abs(frame.yawDegrees) > MAX_YAW_DEGREES || abs(frame.rollDegrees) > MAX_ROLL_DEGREES -> {
            FaceGuidance.LOOK_STRAIGHT
        }
        else -> FaceGuidance.READY_CANDIDATE
    }
}
