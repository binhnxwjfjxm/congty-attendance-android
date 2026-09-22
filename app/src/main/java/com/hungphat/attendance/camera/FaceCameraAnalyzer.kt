package com.hungphat.attendance.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FaceCameraAnalyzer(
    private val detector: FaceDetector,
    private val onStateChanged: (FaceScanState) -> Unit,
    private val onFrameChanged: ((FaceFrame) -> Unit)? = null,
    private val onFaceSample: ((FaceSample) -> Unit)? = null,
) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)
    private var stableFrames = 0
    private var lastState: FaceScanState? = null
    private var lastSampleAt = 0L

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

                if (
                    face != null &&
                    frame.faceWidthRatio >= MIN_SAMPLE_FACE_RATIO &&
                    abs(frame.rollDegrees) <= MAX_SAMPLE_ROLL
                ) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSampleAt >= SAMPLE_INTERVAL_MS) {
                        lastSampleAt = now
                        createFaceSample(imageProxy, face.boundingBox, rotation, frame)
                            ?.let { onFaceSample?.invoke(it) }
                    }
                }

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

    private fun createFaceSample(
        imageProxy: ImageProxy,
        detectedRect: Rect,
        rotationDegrees: Int,
        frame: FaceFrame,
    ): FaceSample? = runCatching {
        val source = imageProxy.toBitmap()
        val upright = if (rotationDegrees == 0) {
            source
        } else {
            Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                Matrix().apply { postRotate(rotationDegrees.toFloat()) },
                true,
            )
        }

        val paddingX = (detectedRect.width() * FACE_PADDING_RATIO).toInt()
        val paddingY = (detectedRect.height() * FACE_PADDING_RATIO).toInt()
        val left = max(0, detectedRect.left - paddingX)
        val top = max(0, detectedRect.top - paddingY)
        val right = min(upright.width, detectedRect.right + paddingX)
        val bottom = min(upright.height, detectedRect.bottom + paddingY)
        if (right <= left || bottom <= top) return@runCatching null

        val crop = Bitmap.createBitmap(upright, left, top, right - left, bottom - top)
        if (upright !== source) source.recycle()
        FaceSample(bitmap = crop, frame = frame)
    }.getOrNull()

    private companion object {
        const val REQUIRED_STABLE_FRAMES = 5
        const val SAMPLE_INTERVAL_MS = 350L
        const val MIN_SAMPLE_FACE_RATIO = 0.24f
        const val MAX_SAMPLE_ROLL = 25f
        const val FACE_PADDING_RATIO = 0.18f
    }
}
