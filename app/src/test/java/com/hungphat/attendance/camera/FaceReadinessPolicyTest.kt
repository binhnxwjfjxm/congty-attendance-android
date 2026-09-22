package com.hungphat.attendance.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceReadinessPolicyTest {
    @Test
    fun noFace_waitsForFace() {
        assertEquals(
            FaceGuidance.NO_FACE,
            FaceReadinessPolicy.evaluate(FaceFrame(faceCount = 0)),
        )
    }

    @Test
    fun multipleFaces_requiresOnePerson() {
        assertEquals(
            FaceGuidance.MULTIPLE_FACES,
            FaceReadinessPolicy.evaluate(FaceFrame(faceCount = 2)),
        )
    }

    @Test
    fun smallFace_requiresMovingCloser() {
        assertEquals(
            FaceGuidance.MOVE_CLOSER,
            FaceReadinessPolicy.evaluate(
                FaceFrame(faceCount = 1, faceWidthRatio = 0.20f),
            ),
        )
    }

    @Test
    fun turnedFace_requiresLookingStraight() {
        assertEquals(
            FaceGuidance.LOOK_STRAIGHT,
            FaceReadinessPolicy.evaluate(
                FaceFrame(
                    faceCount = 1,
                    faceWidthRatio = 0.40f,
                    yawDegrees = 22f,
                ),
            ),
        )
    }

    @Test
    fun centeredCloseFace_isReadyCandidate() {
        assertEquals(
            FaceGuidance.READY_CANDIDATE,
            FaceReadinessPolicy.evaluate(
                FaceFrame(
                    faceCount = 1,
                    faceWidthRatio = 0.40f,
                    yawDegrees = 4f,
                    rollDegrees = 3f,
                ),
            ),
        )
    }
}
