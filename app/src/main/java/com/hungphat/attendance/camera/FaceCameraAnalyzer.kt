package com.hungphat.attendance.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import java.util.concurrent.atomic.AtomicBoolean

class FaceCameraAnalyzer(
    private val detector: FaceDetector,
    private val onStateChanged: (FaceScanState) -> Unit,
    private val onFrameChanged: ((FaceFrame) -> Unit)? = null,
) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)
    private var stableFrames = 0
    private var lastState: FaceScanState? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val uprightFrameWidth =
            if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val face = faces.singleOrNull()
                val frame = FaceFrame(
                    faceCount = faces.size,
                    faceWidthRatio = if (face == null || uprightFrameWidth <= 0) {
                        0f
                    } else {
                        face.boundingBox.width().toFloat() / uprightFrameWidth.toFloat()
                    },
                    yawDegrees = face?.headEulerAngleY ?: 0f,
                    rollDegrees = face?.headEulerAngleZ ?: 0f,
                )

                onFrameChanged?.invoke(frame)

                val nextState = when (FaceReadinessPolicy.evaluate(frame)) {
                    FaceGuidance.NO_FACE -> {
                        stableFrames = 0
                        FaceScanState.WAITING
                    }
                    FaceGuidance.MULTIPLE_FACES -> {
                        stableFrames = 0
                        FaceScanState.MULTIPLE_FACES
                    }
                    FaceGuidance.MOVE_CLOSER -> {
                        stableFrames = 0
                        FaceScanState.MOVE_CLOSER
                    }
                    FaceGuidance.LOOK_STRAIGHT -> {
                        stableFrames = 0
                        FaceScanState.LOOK_STRAIGHT
                    }
                    FaceGuidance.READY_CANDIDATE -> {
                        stableFrames += 1
                        if (stableFrames >= REQUIRED_STABLE_FRAMES) {
                            FaceScanState.READY
                        } else {
                            FaceScanState.HOLD_STILL
                        }
                    }
                }

                if (nextState != lastState) {
                    lastState = nextState
                    onStateChanged(nextState)
                }
            }
            .addOnFailureListener {
                stableFrames = 0
                if (lastState != FaceScanState.WAITING) {
                    lastState = FaceScanState.WAITING
                    onStateChanged(FaceScanState.WAITING)
                }
            }
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    private companion object {
        const val REQUIRED_STABLE_FRAMES = 5
    }
}
