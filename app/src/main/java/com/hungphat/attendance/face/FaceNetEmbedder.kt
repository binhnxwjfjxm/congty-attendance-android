package com.hungphat.attendance.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

class FaceNetEmbedder(context: Context) : AutoCloseable {
    private val interpreter = Interpreter(
        context.assets.open(MODEL_ASSET).use { input ->
            val bytes = input.readBytes()
            ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(bytes)
                    rewind()
                }
        },
        Interpreter.Options().apply {
            setNumThreads(4)
            setUseXNNPACK(true)
        },
    )

    @Synchronized
    fun embed(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val values = FloatArray(pixels.size * 3)
        var cursor = 0
        for (pixel in pixels) {
            values[cursor++] = ((pixel shr 16) and 0xFF).toFloat()
            values[cursor++] = ((pixel shr 8) and 0xFF).toFloat()
            values[cursor++] = (pixel and 0xFF).toFloat()
        }

        val mean = values.average().toFloat()
        var variance = 0.0
        for (value in values) {
            val delta = value - mean
            variance += delta * delta
        }
        var std = sqrt(variance / values.size).toFloat()
        std = max(std, 1f / sqrt(values.size.toFloat()))

        val input = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
        for (value in values) input.putFloat((value - mean) / std)
        input.rewind()

        val output = Array(1) { FloatArray(EMBEDDING_DIMENSIONS) }
        interpreter.run(input, output)

        val vector = output[0]
        var normSquared = 0.0
        for (value in vector) normSquared += value * value
        val norm = sqrt(normSquared).toFloat()
        if (norm > 0f) {
            for (index in vector.indices) vector[index] /= norm
        }
        if (resized !== bitmap) resized.recycle()
        return vector
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        const val MODEL_CODE = "FACENET_128_V1"
        const val EMBEDDING_DIMENSIONS = 128
        private const val MODEL_ASSET = "facenet.tflite"
        private const val INPUT_SIZE = 160
    }
}
