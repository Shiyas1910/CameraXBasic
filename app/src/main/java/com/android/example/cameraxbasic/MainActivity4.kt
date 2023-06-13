package com.android.example.cameraxbasic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class MainActivity4 : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler
    private lateinit var textureView: TextureView

    private val cameraOpenCloseLock = Semaphore(1)
    private var currentZoomLevel = 1f
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main3)

        textureView = findViewById(R.id.texture_view)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread.looper)

        textureView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.x
                    initialTouchY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - initialTouchX
                    val deltaY = event.y - initialTouchY

                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)

                    val rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    val currentWidth = rect?.width()
                    val currentHeight = rect?.height()

                    val deltaZoom = (deltaX + deltaY) / textureView.width.toFloat()

                    currentZoomLevel += deltaZoom

                    if (currentZoomLevel < 1f) {
                        currentZoomLevel = 1f
                    } else if (currentZoomLevel > maxZoom!!) {
                        currentZoomLevel = maxZoom
                    }

                    val zoomRect = createZoomRect(rect!!, currentZoomLevel)
                    val matrix = createTransformMatrix(currentWidth!!, currentHeight!!, zoomRect)

                    val textureTransformMatrix = Matrix()
                    textureView.getTransform(textureTransformMatrix)
                    matrix.postConcat(textureTransformMatrix)

                    textureView.setTransform(matrix)

                    // Update camera zoom and focus areas
                    updateCameraZoom(zoomRect)
                    updateCameraFocus(initialTouchX / textureView.width, initialTouchY / textureView.height)
                }
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        startCamera()
    }

    override fun onPause() {
        closeCamera()
        super.onPause()
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
            return
        }

        val cameraIdList = cameraManager.cameraIdList
        if (cameraIdList.isEmpty()) {
            // No cameras available
            return
        }

        cameraId = cameraIdList[0] // Use the first available camera

        openCamera()
    }

    private fun openCamera() {
        try {
            cameraOpenCloseLock.acquire()

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession()
                    cameraOpenCloseLock.release()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraOpenCloseLock.release()
                    camera.close()
                }
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.")
        }
    }

    private fun createCameraPreviewSession() {
        val texture = textureView.surfaceTexture
        texture?.setDefaultBufferSize(textureView.width, textureView.height)
        val surface = Surface(texture)

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)

        cameraDevice.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    updatePreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    // Configuration failed
                }
            },
            cameraHandler
        )
    }

    private fun updatePreview() {
        captureRequestBuilder.set(
            CaptureRequest.CONTROL_MODE,
            CameraMetadata.CONTROL_MODE_AUTO
        )

        cameraCaptureSession.setRepeatingRequest(
            captureRequestBuilder.build(),
            null,
            cameraHandler
        )
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            cameraCaptureSession.close()
            cameraCaptureSession.device.close()
            cameraThread.quitSafely()
            cameraThread.join()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to close camera.")
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun createZoomRect(rect: Rect, zoomLevel: Float): Rect {
        val currentWidth = rect.width()
        val currentHeight = rect.height()
        val newWidth = (currentWidth / zoomLevel).toInt()
        val newHeight = (currentHeight / zoomLevel).toInt()
        val deltaX = (currentWidth - newWidth) / 2
        val deltaY = (currentHeight - newHeight) / 2
        return Rect(rect.left + deltaX, rect.top + deltaY, rect.right - deltaX, rect.bottom - deltaY)
    }

    private fun createTransformMatrix(currentWidth: Int, currentHeight: Int, zoomRect: Rect): Matrix {
        val matrix = Matrix()
        val centerX = currentWidth / 2f
        val centerY = currentHeight / 2f
        val previewRect = RectF(0f, 0f, currentWidth.toFloat(), currentHeight.toFloat())
        val zoomRectF = RectF(zoomRect)
        matrix.setRectToRect(zoomRectF, previewRect, Matrix.ScaleToFit.FILL)
        matrix.postTranslate(centerX - zoomRectF.centerX(), centerY - zoomRectF.centerY())
        return matrix
    }

    private fun updateCameraZoom(zoomRect: Rect) {
        captureRequestBuilder.set(
            CaptureRequest.SCALER_CROP_REGION,
            zoomRect
        )
        cameraCaptureSession.setRepeatingRequest(
            captureRequestBuilder.build(),
            null,
            cameraHandler
        )
    }

    private fun updateCameraFocus(touchX: Float, touchY: Float) {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val x = touchX * sensorArraySize!!.width().toFloat()
        val y = touchY * sensorArraySize.height().toFloat()

        val halfTouchWidth = 150
        val halfTouchHeight = 150
        val focusAreaTouch = MeteringRectangle(
            Math.max(x.toInt() - halfTouchWidth, 0),
            Math.max(y.toInt() - halfTouchHeight, 0),
            halfTouchWidth * 2,
            halfTouchHeight * 2,
            MeteringRectangle.METERING_WEIGHT_MAX - 1
        )

        captureRequestBuilder.set(
            CaptureRequest.CONTROL_AF_REGIONS,
            arrayOf(focusAreaTouch)
        )

        captureRequestBuilder.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_AUTO
        )

        cameraCaptureSession.setRepeatingRequest(
            captureRequestBuilder.build(),
            null,
            cameraHandler
        )
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1
    }
}
