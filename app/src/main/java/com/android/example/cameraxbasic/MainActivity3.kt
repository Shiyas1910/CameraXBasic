package com.android.example.cameraxbasic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore


val TAG = MainActivity::class.simpleName
const val CAMERA_REQUEST_RESULT = 1

class MainActivity3 : AppCompatActivity() {

    private lateinit var textureView: CameraTextureView
    private lateinit var cameraId: String
    private lateinit var backgroundHandlerThread: HandlerThread
    private lateinit var backgroundHandler: Handler
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var imageReader: ImageReader
    private lateinit var previewSize: Size
    private lateinit var videoSize: Size
    private var shouldProceedWithOnResume: Boolean = true
    private var orientations : SparseIntArray = SparseIntArray(4).apply {
        append(Surface.ROTATION_0, 0)
        append(Surface.ROTATION_90, 90)
        append(Surface.ROTATION_180, 180)
        append(Surface.ROTATION_270, 270)
    }

    private lateinit var mediaRecorder: MediaRecorder
    private var isRecording: Boolean = false

    private val cameraOpenCloseLock = Semaphore(1)
    private var currentZoomLevel = 1f
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private lateinit var cameraThread: HandlerThread
    private lateinit var cameraHandler: Handler

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main3)

        textureView = findViewById(R.id.texture_view)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread.looper)

        findViewById<Button>(R.id.take_photo_btn).apply {
            setOnClickListener {
                takePhoto()
            }
        }

        findViewById<Button>(R.id.record_video_btn).apply {
            setOnClickListener {
                if (isRecording) {
                    mediaRecorder.stop()
                    mediaRecorder.reset()
                } else {
                    mediaRecorder = MediaRecorder()
                    setupMediaRecorder()
                    startRecording()
                }

                isRecording = !isRecording
            }
        }

        if (!wasCameraPermissionWasGiven()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_RESULT)
        }

        /*val scaleGestureDetector = ScaleGestureDetector(requireContext(), listener)

        textureView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }*/

        /*textureView.setOnTouchListener { _, event ->
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

                    val deltaZoom = (deltaX - 500) / textureView.width.toFloat()

                    currentZoomLevel += deltaZoom
                    Log.e("On movement", "$currentZoomLevel - $deltaZoom")

                    if (currentZoomLevel < 1f) {
                        currentZoomLevel = 1f
                    } else if (currentZoomLevel > maxZoom!!) {
                        currentZoomLevel = maxZoom
                    }

                    val zoomRect = createZoomRect(rect!!, currentZoomLevel)
                    val matrix = createTransformMatrix(currentWidth!!, currentHeight!!, zoomRect)
                    Log.e("On movement", "$currentWidth - $currentHeight - ${zoomRect.width()} - ${zoomRect.height()}")

                    val textureTransformMatrix = Matrix()
                    textureView.getTransform(textureTransformMatrix)
                    matrix.postConcat(textureTransformMatrix)

                    textureView.setTransform(matrix)

                    // Update camera zoom and focus areas
                    updateCameraZoom(zoomRect)
                    updateCameraFocus(initialTouchX / textureView.width, initialTouchY / textureView.height)

                    *//*val deltaX = event.x - initialTouchX
                    val deltaY = event.y - initialTouchY

                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)

                    val rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    val currentWidth = rect?.width()
                    val currentHeight = rect?.height()

                    val deltaZoom = (deltaX + deltaY) / textureView.width.toFloat()

                    // Adjust the sensitivity factor to control the panning speed
                    val sensitivityFactor = 0.05f

                    // Calculate the desired panning amount based on the sensitivity factor
                    val desiredPanAmountX = deltaX * sensitivityFactor
                    val desiredPanAmountY = deltaY * sensitivityFactor

                    // Calculate the maximum panning amount based on the current zoom level
                    val maxPanAmountX = (textureView.width * (1f - 1f / currentZoomLevel)) / 2f
                    val maxPanAmountY = (textureView.height * (1f - 1f / currentZoomLevel)) / 2f

                    // Apply the desired panning amount, limiting it within the maximum panning amount
                    val panAmountX = desiredPanAmountX.coerceIn(-maxPanAmountX, maxPanAmountX)
                    val panAmountY = desiredPanAmountY.coerceIn(-maxPanAmountY, maxPanAmountY)

                    // Calculate the panning ratio based on the pan amount and the texture view size
                    val panRatioX = panAmountX / textureView.width.toFloat()
                    val panRatioY = panAmountY / textureView.height.toFloat()

                    // Apply the panning ratio to the zoom and focus areas
                    val newZoomLevel = currentZoomLevel + panRatioX
                    val newFocusX = initialTouchX / textureView.width + panRatioX
                    val newFocusY = initialTouchY / textureView.height + panRatioY

                    currentZoomLevel = newZoomLevel.coerceIn(1f, maxZoom!!)
                    initialTouchX = textureView.width * newFocusX
                    initialTouchY = textureView.height * newFocusY

                    val zoomRect = createZoomRect(rect!!, currentZoomLevel)
                    val matrix = createTransformMatrix(currentWidth!!, currentHeight!!, zoomRect)

                    val textureTransformMatrix = Matrix()
                    textureView.getTransform(textureTransformMatrix)
                    matrix.postConcat(textureTransformMatrix)

                    textureView.setTransform(matrix)

                    // Update camera zoom and focus areas
                    updateCameraZoom(zoomRect)
                    updateCameraFocus(newFocusX, newFocusY)*//*
                }
            }
            true
        }*/

        startBackgroundThread()
    }

    /*private val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = camera!!.cameraInfo.zoomState.value!!.zoomRatio * detector.scaleFactor
            camera!!.cameraControl.setZoomRatio(scale)
            return true
        }
    }*/

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable && shouldProceedWithOnResume) {
            setupCamera()
        } else if (!textureView.isAvailable){
            textureView.surfaceTextureListener = surfaceTextureListener
        }
        shouldProceedWithOnResume = !shouldProceedWithOnResume
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

    private fun setupCamera() {
        val cameraIds: Array<String> = cameraManager.cameraIdList

        for (id in cameraIds) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(id)

            //If we want to choose the rear facing camera instead of the front facing one
            if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                continue
            }

            val streamConfigurationMap : StreamConfigurationMap? = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            if (streamConfigurationMap != null) {
                previewSize = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.height * it.width }!!
                videoSize = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(MediaRecorder::class.java).maxByOrNull { it.height * it.width }!!
                imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.JPEG, 1)
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
            }
            cameraId = id
        }
    }

    private fun wasCameraPermissionWasGiven() : Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        {
            return true
        }

        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            surfaceTextureListener.onSurfaceTextureAvailable(textureView.surfaceTexture!!, textureView.width, textureView.height)
        } else {
            Toast.makeText(
                this,
                "Camera permission is needed to run this application",
                Toast.LENGTH_LONG
            )
                .show()
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.CAMERA
                )) {
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = Uri.fromParts("package", this.packageName, null)
                startActivity(intent)
            }
        }
    }

    private fun takePhoto() {
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder.addTarget(imageReader.surface)
        val rotation = windowManager.defaultDisplay.rotation
        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientations.get(rotation))
        cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, null)
    }

    @SuppressLint("MissingPermission")
    private fun connectCamera() {
        cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
    }

    private fun setupMediaRecorder() {
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setVideoSize(videoSize.width, videoSize.height)
        mediaRecorder.setVideoFrameRate(30)
        mediaRecorder.setOutputFile(createFile().absolutePath)
        mediaRecorder.setVideoEncodingBitRate(10_000_000)
        mediaRecorder.prepare()
    }

    private fun startRecording() {
        val surfaceTexture : SurfaceTexture? = textureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface: Surface = Surface(surfaceTexture)
        val recordingSurface = mediaRecorder.surface
        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilder.addTarget(previewSurface)
        captureRequestBuilder.addTarget(recordingSurface)

        cameraDevice.createCaptureSession(listOf(previewSurface, recordingSurface), captureStateVideoCallback, backgroundHandler)
    }

    /**
     * Surface Texture Listener
     */

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        @SuppressLint("MissingPermission")
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            if (wasCameraPermissionWasGiven()) {
                setupCamera()
                connectCamera()
            }
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {

        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {

        }
    }

    /**
     * Camera State Callbacks
     */

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            val surfaceTexture : SurfaceTexture? = textureView.surfaceTexture
            surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface: Surface = Surface(surfaceTexture)

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(previewSurface)

            cameraDevice.createCaptureSession(listOf(previewSurface, imageReader.surface), captureStateCallback, null)
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {

        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            val errorMsg = when(error) {
                ERROR_CAMERA_DEVICE -> "Fatal (device)"
                ERROR_CAMERA_DISABLED -> "Device policy"
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_CAMERA_SERVICE -> "Fatal (service)"
                ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                else -> "Unknown"
            }
            Log.e(TAG, "Error when trying to connect camera $errorMsg")
        }
    }

    /**
     * Background Thread
     */
    private fun startBackgroundThread() {
        backgroundHandlerThread = HandlerThread("CameraVideoThread")
        backgroundHandlerThread.start()
        backgroundHandler = Handler(backgroundHandlerThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread.quitSafely()
        backgroundHandlerThread.join()
    }

    /**
     * Capture State Callback
     */

    private val captureStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {

        }
        override fun onConfigured(session: CameraCaptureSession) {
            cameraCaptureSession = session

            cameraCaptureSession.setRepeatingRequest(
                captureRequestBuilder.build(),
                null,
                backgroundHandler
            )
        }
    }

    private val captureStateVideoCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Configuration failed")
        }
        override fun onConfigured(session: CameraCaptureSession) {
            cameraCaptureSession = session
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            try {
                cameraCaptureSession.setRepeatingRequest(
                    captureRequestBuilder.build(), null,
                    backgroundHandler
                )
                mediaRecorder.start()
            } catch (e: CameraAccessException) {
                e.printStackTrace()
                Log.e(TAG, "Failed to start camera preview because it couldn't access the camera")
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }

        }
    }

    /**
     * Capture Callback
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {}

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) { }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {

        }
    }

    /**
     * ImageAvailable Listener
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        Toast.makeText(this@MainActivity3, "Photo Taken!", Toast.LENGTH_SHORT).show()
        val image: Image = reader.acquireLatestImage()
        image.close()
    }

    /**
     * File Creation
     */

    private fun createFile(): File {
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        return File(filesDir, "VID_${sdf.format(Date())}.mp4")
    }
}