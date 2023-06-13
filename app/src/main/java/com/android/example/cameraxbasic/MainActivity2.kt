package com.android.example.cameraxbasic

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.databinding.DataBindingUtil
import com.android.example.cameraxbasic.databinding.ActivityMain2Binding

class MainActivity2 : AppCompatActivity() {
    private lateinit var cameraXHelper: CameraXHelper
//    private lateinit var mViewBinding: ActivityMain2Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        mViewBinding = DataBindingUtil.setContentView(this, R.layout.activity_main2
        setContentView(R.layout.activity_main2)

        val buttonPanLeft = findViewById<Button>(R.id.buttonLeft)
        val buttonPanRight = findViewById<Button>(R.id.buttonRight)
        val viewFinder = findViewById<PanningPreviewView>(R.id.view_finder)

        // Initialize the CameraXHelper
        cameraXHelper = CameraXHelper(this, this, viewFinder)
//        viewFinder.setCameraControl(cameraXHelper.getCameraControl()!!)

        // Example usage:
        buttonPanLeft.setOnClickListener {
            cameraXHelper.panLeft()
        }

        buttonPanRight.setOnClickListener {
            cameraXHelper.panRight()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraXHelper.release()
    }
}