package com.arbortag.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.arbortag.app.databinding.ActivityTreeTaggingBinding
import com.arbortag.app.utils.ArUcoMeasurementHelper
import org.opencv.aruco.Aruco
import org.opencv.core.Core
import java.io.File

class TreeTaggingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTreeTaggingBinding
    private var imageCapture: ImageCapture? = null
    private var projectId: Long = -1
    private var capturedImagePath: String? = null
    private var capturedBitmap: Bitmap? = null

    // ArUco measurement helper
    private var arucoHelper: ArUcoMeasurementHelper? = null

    // Measurement state
    private val measurementPoints = mutableListOf<Pair<Float, Float>>()
    private var currentMeasurementMode: MeasurementMode = MeasurementMode.NONE
    private var heightMeters: Double? = null
    private var widthMeters: Double? = null
    private var canopyMeters: Double? = null

    private val TAG = "TreeTaggingActivity"

    enum class MeasurementMode {
        NONE, HEIGHT, WIDTH, CANOPY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTreeTaggingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getLongExtra("project_id", -1)

        // TEST OpenCV & ArUco before proceeding
        testOpenCVAndArUco()

        // Initialize ArUco helper with marker SIZE (side length) from settings
        val markerSize = SettingsActivity.getMarkerSize(this)
        arucoHelper = ArUcoMeasurementHelper(markerSize)
        Log.d(TAG, "ArUco helper initialized with marker size: $markerSize m")

        setupCamera()
        setupClickListeners()
    }

    /**
     * Test OpenCV and ArUco availability
     * This helps diagnose issues early
     */
    private fun testOpenCVAndArUco() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Testing OpenCV & ArUco in TreeTagging")
        Log.d(TAG, "========================================")

        var allOk = true

        try {
            // Test 1: OpenCV version
            val opencvVersion = Core.getVersionString()
            Log.d(TAG, "✓ OpenCV version: $opencvVersion")

        } catch (e: Exception) {
            Log.e(TAG, "✗ Could not get OpenCV version: ${e.message}")
            allOk = false
        }

        try {
            // Test 2: Can create ArUco dictionary?
            val dict = Aruco.getPredefinedDictionary(Aruco.DICT_6X6_250)
            Log.d(TAG, "✓ ArUco dictionary created")

        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "✗ ArUco NATIVE LIBRARY ERROR!", e)
            allOk = false
            showArUcoErrorDialog()

        } catch (e: Exception) {
            Log.e(TAG, "✗ ArUco dictionary error: ${e.message}", e)
            allOk = false
        }

        if (allOk) {
            Log.d(TAG, "✓ All systems ready for ArUco detection")
            Log.d(TAG, "========================================")
        } else {
            Log.e(TAG, "✗ System check FAILED - ArUco may not work")
            Log.e(TAG, "========================================")
        }
    }

    private fun showArUcoErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("ArUco Not Available")
            .setMessage(
                "The ArUco marker detection library is not working.\n\n" +
                        "Height measurements using markers will not work.\n\n" +
                        "Possible solutions:\n" +
                        "1. Update OpenCV dependency in build.gradle\n" +
                        "2. Use official OpenCV Android SDK\n" +
                        "3. Check that opencv-contrib is included"
            )
            .setPositiveButton("Continue Anyway") { _, _ -> }
            .setNegativeButton("Go Back") { _, _ -> finish() }
            .show()
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

        binding.btnRecalibrate.setOnClickListener {
            detectMarker()
        }

        setupImageViewTouchListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupImageViewTouchListener() {
        binding.ivCapturedImage.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN &&
                currentMeasurementMode != MeasurementMode.NONE) {

                if (arucoHelper?.isCalibrated() != true) {
                    Toast.makeText(
                        this,
                        "Please detect ArUco marker first by clicking 'Detect Marker'",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnTouchListener true
                }

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
        if (arucoHelper?.isCalibrated() != true) {
            Toast.makeText(
                this,
                "⚠️ Please detect ArUco marker first!\n\nClick 'Detect Marker' button",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        currentMeasurementMode = mode
        measurementPoints.clear()

        val instruction = when (mode) {
            MeasurementMode.HEIGHT -> "Tap top and bottom of tree"
            MeasurementMode.WIDTH -> "Tap left and right edges of trunk"
            MeasurementMode.CANOPY -> "Tap two edges of canopy"
            else -> ""
        }

        Toast.makeText(this, "📍 $instruction", Toast.LENGTH_LONG).show()
    }

    private fun drawPointOnImage(x: Float, y: Float) {
        capturedBitmap?.let { bitmap ->
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
            }
            canvas.drawCircle(x, y, 15f, paint)

            // Draw line if we have 2 points
            if (measurementPoints.size == 1) {
                val linePaint = Paint().apply {
                    color = Color.GREEN
                    strokeWidth = 5f
                }
                canvas.drawLine(
                    measurementPoints[0].first,
                    measurementPoints[0].second,
                    x, y,
                    linePaint
                )
            }

            binding.ivCapturedImage.setImageBitmap(mutableBitmap)
            capturedBitmap = mutableBitmap
        }
    }

    private fun calculateMeasurement() {
        if (measurementPoints.size < 2) return

        val point1 = measurementPoints[0]
        val point2 = measurementPoints[1]

        try {
            // Use ArUco helper to calculate real distance
            val realDistance = arucoHelper?.calculateDistance(point1, point2) ?: 0.0

            // Annotate the image
            capturedBitmap = arucoHelper?.annotateMeasurement(
                capturedBitmap!!,
                point1,
                point2,
                realDistance
            )
            binding.ivCapturedImage.setImageBitmap(capturedBitmap)

            when (currentMeasurementMode) {
                MeasurementMode.HEIGHT -> {
                    heightMeters = realDistance
                    binding.tvHeightValue.text = String.format("%.2f m", realDistance)
                    binding.tvHeightValue.setTextColor(getColor(R.color.success))
                }
                MeasurementMode.WIDTH -> {
                    widthMeters = realDistance
                    binding.tvWidthValue.text = String.format("%.2f m", realDistance)
                    binding.tvWidthValue.setTextColor(getColor(R.color.success))
                }
                MeasurementMode.CANOPY -> {
                    canopyMeters = realDistance
                    binding.tvCanopyValue.text = String.format("%.2f m", realDistance)
                    binding.tvCanopyValue.setTextColor(getColor(R.color.success))
                }
                else -> {}
            }

            Toast.makeText(
                this,
                "✓ ${String.format("%.2f m", realDistance)} measured",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Log.e(TAG, "Measurement error: ${e.message}", e)
            Toast.makeText(
                this,
                "❌ Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun detectMarker() {
        capturedBitmap?.let { bitmap ->
            binding.progressBar.visibility = View.VISIBLE
            binding.tvMarkerStatus.text = "⌛ Detecting marker..."
            binding.tvMarkerStatus.setTextColor(getColor(R.color.warning))

            // Run detection in background thread
            Thread {
                val detected = arucoHelper?.detectMarkerAndCalculateRatio(bitmap) ?: false

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE

                    if (detected) {
                        binding.tvMarkerStatus.text = "✓ Marker Detected"
                        binding.tvMarkerStatus.setTextColor(getColor(R.color.success))
                        binding.tvPixelRatio.text = String.format(
                            "Ratio: %.2f px/m",
                            arucoHelper?.getPixelToMeterRatio() ?: 0.0
                        )
                        binding.tvPixelRatio.visibility = View.VISIBLE
                        binding.layoutMeasurementButtons.visibility = View.VISIBLE

                        Toast.makeText(
                            this,
                            "✓ ArUco marker detected!\nYou can now measure tree dimensions.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        binding.tvMarkerStatus.text = "✗ No Marker Found"
                        binding.tvMarkerStatus.setTextColor(getColor(R.color.error))
                        binding.tvPixelRatio.visibility = View.GONE
                        binding.layoutMeasurementButtons.visibility = View.GONE

                        showMarkerNotFoundDialog()
                    }
                }
            }.start()
        }
    }

    private fun showMarkerNotFoundDialog() {
        AlertDialog.Builder(this)
            .setTitle("ArUco Marker Not Detected")
            .setMessage(
                "No ArUco marker was found in the image.\n\n" +
                        "Checklist:\n" +
                        "✓ Marker is DICT_6X6_250 (ID 0-249)\n" +
                        "✓ Marker is clearly visible and in focus\n" +
                        "✓ Good lighting (not too dark/bright)\n" +
                        "✓ Marker is flat (not curved/wrinkled)\n" +
                        "✓ Marker size in Settings is correct\n" +
                        "✓ Entire marker is in frame\n\n" +
                        "What to do:\n" +
                        "1. Retake photo with better conditions\n" +
                        "2. Check Settings for marker size\n" +
                        "3. Try detection again"
            )
            .setPositiveButton("Retake Photo") { _, _ ->
                // Reset to camera mode
                binding.previewView.visibility = View.VISIBLE
                binding.layoutMeasurement.visibility = View.GONE
                binding.btnCapture.visibility = View.VISIBLE
            }
            .setNeutralButton("Settings") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Try Again") { _, _ ->
                detectMarker()
            }
            .show()
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

            // IMPROVED: Better image capture settings
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetResolution(android.util.Size(1920, 1080))
                .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
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

                Log.d(TAG, "Camera initialized successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed: ${e.message}", e)
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "tree_${System.currentTimeMillis()}.jpg"
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
                        "📸 Image captured! Detecting ArUco marker...",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Automatically try to detect marker
                    detectMarker()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                    Toast.makeText(
                        this@TreeTaggingActivity,
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun displayCapturedImage(imageFile: File) {
        capturedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        binding.ivCapturedImage.setImageBitmap(capturedBitmap)
        Log.d(TAG, "Image loaded: ${capturedBitmap?.width}x${capturedBitmap?.height}")
    }

    private fun proceedToSpeciesSelection() {
        val height = heightMeters
        val width = widthMeters

        if (height == null || width == null) {
            Toast.makeText(
                this,
                "⚠️ Please measure both height and width",
                Toast.LENGTH_LONG
            ).show()
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