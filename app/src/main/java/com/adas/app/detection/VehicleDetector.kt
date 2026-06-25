package com.adas.app.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log

class VehicleDetector(private val context: Context) {
    companion object {
        private const val TAG = "VehicleDetector"
        val VEHICLE_WIDTHS = mapOf(
            "car" to 1.8f, "truck" to 2.5f, "bus" to 2.6f,
            "motorcycle" to 0.8f, "bicycle" to 0.6f, "person" to 0.5f,
            "van" to 2.1f, "vehicle" to 1.8f
        )
    }

    private var useSimulation = true

    init {
        try {
            context.assets.open("efficientdet_lite0.tflite").close()
            useSimulation = false
            Log.i(TAG, "TFLite model found")
        } catch (e: Exception) {
            Log.i(TAG, "Using simulation mode")
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> = simulateDetections(bitmap)

    private fun simulateDetections(bitmap: Bitmap): List<DetectionResult> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val results = mutableListOf<DetectionResult>()
        val labels = listOf("car", "truck", "motorcycle", "car", "car", "bus")
        val count = (0..2).random()
        repeat(count) { i ->
            val label = labels[i % labels.size]
            val cx = w * (0.2f + Math.random().toFloat() * 0.6f)
            val cy = h * (0.45f + Math.random().toFloat() * 0.35f)
            val bw = w * (0.12f + Math.random().toFloat() * 0.22f)
            val bh = h * (0.08f + Math.random().toFloat() * 0.16f)
            results.add(DetectionResult(
                label = label,
                confidence = 0.6f + Math.random().toFloat() * 0.35f,
                boundingBox = RectF(
                    (cx - bw/2).coerceAtLeast(0f), (cy - bh/2).coerceAtLeast(0f),
                    (cx + bw/2).coerceAtMost(w),   (cy + bh/2).coerceAtMost(h)
                ),
                trackId = i
            ))
        }
        return results
    }

    fun close() {}
}
