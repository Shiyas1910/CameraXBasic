package com.android.example.cameraxbasic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Rational
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.android.example.cameraxbasic.fragments.CameraFragment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraXHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PanningPreviewView
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraSelector: CameraSelector? = null
    private var cameraControl: CameraControl? = null
    private var imageCapture: ImageCapture? = null
    private var orientationEventListener: OrientationEventListener? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraExecutor: ExecutorService? = null
    private var orientation: Int = 0

    init {
        // Request camera permissions if not granted
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw IllegalStateException("Camera permission not granted.")
        }

        // Initialize camera thread and executor
        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Set up orientation event listener
        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                this@CameraXHelper.orientation = rotation
//                cameraControl.(rotation)
            }
        }

        // Start the camera when the view is ready
        previewView.post { startCamera() }

        // Register the orientation event listener
        orientationEventListener?.enable()
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - CameraFragment.RATIO_4_3_VALUE) <= abs(previewRatio - CameraFragment.RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Set up preview use case
            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio(previewView.width, previewView.height))
//                .setTargetResolution(Size(previewView.width, previewView.height))
                .setTargetRotation(orientation)
                .build()
                .also { it.setSurfaceProvider(previewView.getSurfaceProvider()) }

            // Set up image capture use case
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Select the back camera as the default
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()

                // Bind the camera use cases
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector!!,
                    preview,
                    imageCapture
                )

                cameraControl = camera?.cameraControl
            } catch (exc: Exception) {
                // Handle exceptions here
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun getCameraControl(): CameraControl? {
        return this.cameraControl
    }

    fun panLeft() {
        cameraHandler?.post {
            // Adjust the camera pan to the left here
            // For example: cameraControl?.setLinearZoom(0.5f)
            cameraControl?.setLinearZoom(0.5f)
        }
    }

    fun panRight() {
        cameraHandler?.post {
            // Adjust the camera pan to the right here
            // For example: cameraControl?.setLinearZoom(1.5f)
            cameraControl?.setLinearZoom(1.0f)
        }
    }

    fun release() {
        cameraExecutor?.shutdown()
        cameraThread?.quitSafely()
        orientationEventListener?.disable()
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        cameraControl = null
        imageCapture = null
        orientationEventListener = null
        cameraThread = null
        cameraHandler = null
        cameraExecutor = null
    }
}
