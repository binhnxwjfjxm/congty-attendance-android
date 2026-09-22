package com.hungphat.attendance.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.hungphat.attendance.camera.FaceCameraAnalyzer
import com.hungphat.attendance.camera.FaceFrame
import com.hungphat.attendance.camera.FaceSample
import com.hungphat.attendance.camera.FaceScanState
import java.util.concurrent.Executors

@Composable
fun FaceCameraPreview(
    modifier: Modifier = Modifier,
    onStateChanged: (FaceScanState) -> Unit,
    onFrameChanged: ((FaceFrame) -> Unit)? = null,
    onFaceSample: ((FaceSample) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize(),
    )

    DisposableEffect(lifecycleOwner, previewView) {
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            if (disposed) return@Runnable
            provider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        analysisExecutor,
                        FaceCameraAnalyzer(
                            detector = detector,
                            onStateChanged = { state ->
                                mainExecutor.execute { onStateChanged(state) }
                            },
                            onFrameChanged = onFrameChanged?.let { callback ->
                                { frame -> mainExecutor.execute { callback(frame) } }
                            },
                            onFaceSample = onFaceSample?.let { callback ->
                                { sample -> mainExecutor.execute { callback(sample) } }
                            },
                        ),
                    )
                }

            try {
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis,
                )
            } catch (_: Exception) {
                onStateChanged(FaceScanState.WAITING)
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            disposed = true
            provider?.unbindAll()
            detector.close()
            analysisExecutor.shutdown()
        }
    }
}
