package com.example.orderease

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var cropFrame: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewFinder = findViewById(R.id.viewFinder)
        cropFrame = findViewById(R.id.crop_frame)
        val captureBtn = findViewById<ImageButton>(R.id.capture_btn)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        captureBtn.setOnClickListener { takePhoto() }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("Camera", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(externalCacheDir, "temp_photo.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processAndCropImage(photoFile)
                }
            }
        )
    }

    private fun processAndCropImage(file: File) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        
        // Handle rotation using EXIF
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> {
                // Fallback for devices that don't set EXIF orientation but capture in landscape sensor orientation
                if (bitmap.width > bitmap.height) {
                    matrix.postRotate(90f)
                }
            }
        }
        
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Mapping PreviewView coordinates to Bitmap coordinates
        val vw = viewFinder.width.toFloat()
        val vh = viewFinder.height.toFloat()
        val bw = rotatedBitmap.width.toFloat()
        val bh = rotatedBitmap.height.toFloat()

        // PreviewView ScaleType is FILL_CENTER by default
        val scale = Math.max(vw / bw, vh / bh)
        val dx = (vw - bw * scale) / 2f
        val dy = (vh - bh * scale) / 2f

        // Map crop_frame from UI space to bitmap space
        val cropX = (cropFrame.left - dx) / scale
        val cropY = (cropFrame.top - dy) / scale
        val cropW = cropFrame.width / scale
        val cropH = cropFrame.height / scale

        try {
            val finalX = cropX.toInt().coerceIn(0, rotatedBitmap.width - 1)
            val finalY = cropY.toInt().coerceIn(0, rotatedBitmap.height - 1)
            val finalW = cropW.toInt().coerceAtMost(rotatedBitmap.width - finalX)
            val finalH = cropH.toInt().coerceAtMost(rotatedBitmap.height - finalY)

            val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, finalX, finalY, finalW, finalH)
            
            val croppedFile = File(externalCacheDir, "cropped_photo.jpg")
            FileOutputStream(croppedFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val resultIntent = Intent()
            resultIntent.putExtra("CROP_PATH", croppedFile.absolutePath)
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Log.e("Camera", "Error cropping image", e)
            val resultIntent = Intent()
            resultIntent.putExtra("CROP_PATH", file.absolutePath)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
