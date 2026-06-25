package com.adas.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.SingleCameraConfig
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Dual-camera manager using CameraX ConcurrentCamera API (CameraX 1.3+).
 *
 * Key rule: both cameras must be bound in ONE call using
 * ProcessCameraProvider.bindToLifecycle(List<SingleCameraConfig>).
 * Two separate bindToLifecycle() calls conflict — only one camera opens.
 *
 * Falls back to single back-camera if concurrent camera is not supported
 * by the device hardware or only one camera is enabled in settings.
 */
class DualCameraManager(private val context: Context) {

    companion object {
        private const val TAG = "DualCameraManager"
        private val ANALYSIS_RES = Size(640, 480)
        private const val SKIP = 3  // analyse every Nth frame
    }

    private var provider: ProcessCameraProvider? = null
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)

    /** Called with each processed Bitmap from the back lens (road ahead) */
    var onFrontFrame: ((Bitmap, Long) -> Unit)? = null

    /** Called with each processed Bitmap from the front lens (rear traffic) */
    var onRearFrame: ((Bitmap, Long) -> Unit)? = null

    var frontEnabled = true
    var rearEnabled  = true

    private var nFront = 0
    private var nRear  = 0

    fun start(owner: LifecycleOwner, frontPV: PreviewView?, rearPV: PreviewView?) {
        ProcessCameraProvider.getInstance(context).addListener({
            provider = ProcessCameraProvider.getInstance(context).get()
            bind(owner, frontPV, rearPV)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(owner: LifecycleOwner, frontPV: PreviewView?, rearPV: PreviewView?) {
        val p = provider ?: return
        try {
            p.unbindAll()

            // ── Back camera use-cases (road ahead = main preview) ─────────────
            val backPreview = frontPV?.let {
                Preview.Builder().build().also { pr -> pr.setSurfaceProvider(it.surfaceProvider) }
            }
            val backAnalysis = buildAnalysis { bmp ->
                nFront++
                if (nFront % SKIP == 0) onFrontFrame?.invoke(bmp, System.currentTimeMillis())
            }

            // ── Front camera use-cases (rear traffic = PIP) ───────────────────
            val frontPreview = rearPV?.let {
                Preview.Builder().build().also { pr -> pr.setSurfaceProvider(it.surfaceProvider) }
            }
            val frontAnalysis = buildAnalysis { bmp ->
                nRear++
                if (nRear % SKIP == 0) onRearFrame?.invoke(bmp, System.currentTimeMillis())
            }

            // Check hardware concurrent camera support
            val concurrentInfos = p.availableConcurrentCameraInfos
            val hwSupportsDual  = concurrentInfos.any { combo ->
                val lenses = combo.map { it.lensFacing }
                lenses.contains(CameraSelector.LENS_FACING_BACK) &&
                lenses.contains(CameraSelector.LENS_FACING_FRONT)
            }

            if (hwSupportsDual && frontEnabled && rearEnabled) {
                tryBindConcurrent(owner, p, backPreview, backAnalysis, frontPreview, frontAnalysis)
            } else {
                Log.i(TAG, "Concurrent not available — single camera mode")
                bindBackOnly(owner, p, backPreview, backAnalysis)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Bind error: ${e.message}", e)
        }
    }

    private fun tryBindConcurrent(
        owner: LifecycleOwner,
        p: ProcessCameraProvider,
        backPreview: Preview?,
        backAnalysis: ImageAnalysis,
        frontPreview: Preview?,
        frontAnalysis: ImageAnalysis
    ) {
        try {
            val backUseCases  = UseCaseGroup.Builder().apply {
                backPreview?.let { addUseCase(it) }
                addUseCase(backAnalysis)
            }.build()

            val frontUseCases = UseCaseGroup.Builder().apply {
                frontPreview?.let { addUseCase(it) }
                addUseCase(frontAnalysis)
            }.build()

            val configs = listOf(
                SingleCameraConfig(CameraSelector.DEFAULT_BACK_CAMERA,  backUseCases,  owner),
                SingleCameraConfig(CameraSelector.DEFAULT_FRONT_CAMERA, frontUseCases, owner)
            )

            p.bindToLifecycle(configs)
            Log.i(TAG, "✓ Both cameras running concurrently")

        } catch (e: Exception) {
            Log.w(TAG, "Concurrent bind failed: ${e.message} — falling back")
            p.unbindAll()
            bindBackOnly(owner, p, backPreview, backAnalysis)
        }
    }

    private fun bindBackOnly(
        owner: LifecycleOwner,
        p: ProcessCameraProvider,
        preview: Preview?,
        analysis: ImageAnalysis
    ) {
        try {
            val useCases = listOfNotNull(preview, analysis)
            p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
            Log.i(TAG, "Back camera only (concurrent not supported on this device)")
        } catch (e: Exception) {
            Log.e(TAG, "Back-only bind failed: ${e.message}")
        }
    }

    fun rebind(owner: LifecycleOwner, frontPV: PreviewView?, rearPV: PreviewView?) {
        provider?.unbindAll()
        nFront = 0; nRear = 0
        bind(owner, frontPV, rearPV)
    }

    private fun buildAnalysis(onFrame: (Bitmap) -> Unit): ImageAnalysis =
        ImageAnalysis.Builder()
            .setTargetResolution(ANALYSIS_RES)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build().also { analysis ->
                analysis.setAnalyzer(executor) { proxy ->
                    try {
                        val buf   = proxy.planes[0].buffer
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        val bmp   = Bitmap.createBitmap(
                            proxy.width, proxy.height, Bitmap.Config.ARGB_8888
                        )
                        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
                        onFrame(bmp)
                    } catch (e: Exception) {
                        Log.e(TAG, "Frame processing error: ${e.message}")
                    } finally {
                        proxy.close()
                    }
                }
            }

    fun stop() {
        provider?.unbindAll()
        if (!executor.isShutdown) executor.shutdown()
    }
}
