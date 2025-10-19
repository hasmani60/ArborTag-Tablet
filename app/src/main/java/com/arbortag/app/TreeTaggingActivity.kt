package com.arbortag.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.arbortag.app.databinding.ActivityTreeTaggingBinding
import java.io.File
import kotlin.math.sqrt

class TreeTaggingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTreeTaggingBinding
    private var imageCapture: ImageCapture? = null
    private var projectId: Long = -1
    private var capturedImagePath: String? = null
    private var capturedBitmap: Bitmap? = null

    // Measurement state
    private val measurementPoints = mutableListOf<Pair<Float, Float>>()
    private var currentMeasurementMode: MeasurementMode = MeasurementMode.NONE
    private var heightMeters: Double? = null
    private var widthMeters: Double? = null
    private var canopyMeters: Double? = null

    // Simple scale factor (pixels to meters) - simplified without Aruco
    private val SCALE_FACTOR = 100.0 // 100 pixels = 1 meter (approximate)

    enum class MeasurementMode {
        NONE, HEIGHT, WIDTH, CANOPY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTreeTaggingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getLongExtra("project_id", -1)

        setupCamera()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnCapture.setOnClickListener {
            captureImage()
        }

        binding.btnMeasureHeight.setOnClickListener {
            startMeasurement(MeasurementMode.HEIGHT)
        }

        binding.btnMeasureWidth.setOnClickListener {
            startMeasurement(MeasurementMode.WIDTH)
        }

        binding.btnMeasureCanopy.setOnClickListener {
            startMeasurement(MeasurementMode.CANOPY)
        }

        binding.btnNextStep.setOnClickListener {
            proceedToSpeciesSelection()
        }

        setupImageViewTouchListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupImageViewTouchListener() {
        binding.ivCapturedImage.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN &&
                currentMeasurementMode != MeasurementMode.NONE) {

                val x = event.x
                val y = event.y
                measurementPoints.add(Pair(x, y))

                // Draw point on image
                drawPointOnImage(x, y)

                // Check if we have enough points
                if (measurementPoints.size == 2) {
                    calculateMeasurement()
                    measurementPoints.clear()
                    currentMeasurementMode = MeasurementMode.NONE
                }

                true
            } else {
                false
            }
        }
    }

    private fun startMeasurement(mode: MeasurementMode) {
        currentMeasurementMode = mode
        measurementPoints.clear()

        val instruction = when (mode) {
            MeasurementMode.HEIGHT -> "Tap top and bottom of tree"
            MeasurementMode.WIDTH -> "Tap left and right edges of trunk"
            MeasurementMode.CANOPY -> "Tap two edges of canopy"
            else -> ""
        }

        Toast.makeText(this, instruction, Toast.LENGTH_SHORT).show()
    }

    private fun drawPointOnImage(x: Float, y: Float) {
        capturedBitmap?.let { bitmap ->
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
            }
            canvas.drawCircle(x, y, 10f, paint)

            binding.ivCapturedImage.setImageBitmap(mutableBitmap)
            capturedBitmap = mutableBitmap
        }
    }

    private fun calculateMeasurement() {
        if (measurementPoints.size < 2) return

        val point1 = measurementPoints[0]
        val point2 = measurementPoints[1]

        // Calculate pixel distance
        val pixelDistance = sqrt(
            (point2.first - point1.first) * (point2.first - point1.first) +
                    (point2.second - point1.second) * (point2.second - point1.second)
        )

        // Convert to meters using simple scale
        val realDistance = pixelDistance / SCALE_FACTOR

        when (currentMeasurementMode) {
            MeasurementMode.HEIGHT -> {
                heightMeters = realDistance
                binding.tvHeightValue.text = String.format("%.2f m", realDistance)
            }
            MeasurementMode.WIDTH -> {
                widthMeters = realDistance
                binding.tvWidthValue.text = String.format("%.2f m", realDistance)
            }
            MeasurementMode.CANOPY -> {
                canopyMeters = realDistance
                binding.tvCanopyValue.text = String.format("%.2f m", realDistance)
            }
            else -> {}
        }

        Toast.makeText(this, "Measurement saved!", Toast.LENGTH_SHORT).show()
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedImagePath = photoFile.absolutePath
                    displayCapturedImage(photoFile)

                    // Switch UI to measurement mode
                    binding.previewView.visibility = View.GONE
                    binding.layoutMeasurement.visibility = View.VISIBLE
                    binding.btnCapture.visibility = View.GONE

                    Toast.makeText(
                        this@TreeTaggingActivity,
                        "Image captured! Now measure the tree",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@TreeTaggingActivity,
                        "Image capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun displayCapturedImage(imageFile: File) {
        capturedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        binding.ivCapturedImage.setImageBitmap(capturedBitmap)
    }

    private fun proceedToSpeciesSelection() {
        val height = heightMeters
        val width = widthMeters

        if (height == null || width == null) {
            Toast.makeText(this, "Please measure height and width", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, SpeciesSelectionActivity::class.java)
        intent.putExtra("project_id", projectId)
        intent.putExtra("image_path", capturedImagePath)
        intent.putExtra("height", height)
        intent.putExtra("width", width)
        canopyMeters?.let { intent.putExtra("canopy", it) }
        startActivity(intent)
        finish()
    }
}