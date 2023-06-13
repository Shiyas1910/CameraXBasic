package com.android.example.cameraxbasic

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.camera.core.CameraControl
import androidx.camera.core.Preview.SurfaceProvider
import androidx.camera.view.PreviewView

class PanningPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private var previewView: PreviewView
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialScaleX = 0f
    private var initialScaleY = 0f
    private var cameraControl: CameraControl? = null

    init {
        previewView = PreviewView(context, attrs, defStyleAttr)
        addView(previewView)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        previewView.layout(0, 0, width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                initialScaleX = scaleX
                initialScaleY = scaleY
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - initialTouchX
                val deltaY = event.y - initialTouchY

                val maxDeltaX = width.toFloat() - (initialScaleX * width)
                val maxDeltaY = height.toFloat() - (initialScaleY * height)

                val newScaleX = initialScaleX + deltaX / width
                val newScaleY = initialScaleY + deltaY / height

                scaleX = newScaleX.coerceIn(0f, 1f + maxDeltaX / width)
                scaleY = newScaleY.coerceIn(0f, 1f + maxDeltaY / height)

//                cameraControl?.setLinearZoom(scaleX)
            }
        }

        return true
    }

    fun setCameraControl(cameraControl: CameraControl) {
        this.cameraControl = cameraControl
    }

    fun getSurfaceProvider(): SurfaceProvider = previewView.surfaceProvider
}
