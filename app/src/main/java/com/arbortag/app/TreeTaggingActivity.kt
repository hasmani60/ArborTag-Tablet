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

/**
 * TreeTaggingActivity - Main activity for capturing and measuring trees
 *
 * Features:
 * - Camera capture with CameraX
 * - ArUco marker detection and calibration
 * - Interactive measurement (height, width, canopy)
 * - Zoomable image view with pinch-to-zoom
 * - Visual feedback and undo functionality
 *
 * FIXED ISSUES:
 * - View binding using direct references
 * - Memory leak prevention
 * - Resource cleanup
 * - Enhanced user feedback
 */
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

        // Test OpenCV & ArUco before proceeding
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

        // ✅ FIXED: Added undo button handler
        binding.btnUndo.setOnClickListener {
            if (measurementPoints.isNotEmpty()) {
                measurementPoints.removeLast()

                // Redraw image without last point
                capturedBitmap?.let { original ->
                    val mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
                    binding.ivMeasurementImage.setImageBitmap(mutableBitmap)
                }

                Toast.makeText(this, "↩ Last point removed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No points to undo", Toast.LENGTH_SHORT).show()
            }
        }

        setupImageViewTouchListener()
    }

    /**
     * ✅ FIXED: Using direct binding reference instead of findViewById
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupImageViewTouchListener() {
        binding.ivMeasurementImage.setOnTouchListener { _, event ->
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

    /**
     * ✅ ENHANCED: Added visual feedback
     */
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

        val (instruction, emoji) = when (mode) {
            MeasurementMode.HEIGHT -> "Tap top and bottom of tree" to "📏"
            MeasurementMode.WIDTH -> "Tap left and right edges of trunk" to "📐"
            MeasurementMode.CANOPY -> "Tap two edges of canopy" to "🌳"
            else -> "" to ""
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
                strokeWidth = 5f
            }

            canvas.drawCircle(x, y, 15f, paint)

            // Update the displayed image
            capturedBitmap = mutableBitmap
            binding.ivMeasurementImage.setImageBitmap(mutableBitmap)
        }
    }

    /**
     * ✅ ENHANCED: Added null safety checks
     */
    private fun calculateMeasurement() {
        if (measurementPoints.size != 2) return

        // Add null safety check
        if (arucoHelper == null) {
            Toast.makeText(this, "Measurement system not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val realDistance = arucoHelper?.calculateDistance(
                measurementPoints[0],
                measurementPoints[1]
            )

            if (realDistance == null) {
                Toast.makeText(
                    this,
                    "❌ Calibration error. Please detect marker again.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // Update appropriate measurement
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

    /**
     * ✅ FIXED: Using direct binding references
     */
    private fun detectMarker() {
        capturedBitmap?.let { bitmap ->
            binding.progressAruco.visibility = View.VISIBLE

            binding.tvMarkerStatus.text = "⌛ Detecting marker..."
            binding.tvMarkerStatus.setTextColor(getColor(R.color.warning))

            // Run detection in background thread
            Thread {
                val detected = arucoHelper?.detectMarkerAndCalculateRatio(bitmap) ?: false

                runOnUiThread {
                    binding.progressAruco.visibility = View.GONE

                    if (detected) {
                        binding.tvMarkerStatus.text = "✓ Marker Detected"
                        binding.tvMarkerStatus.setTextColor(getColor(R.color.success))
                        binding.tvPixelRatio.text = String.format(
                            "Ratio: %.2f px/m",
                            arucoHelper?.getPixelToMeterRatio() ?: 0.0
                        )
                        binding.tvPixelRatio.visibility = View.VISIBLE

                        binding.measurementToolbar.visibility = View.VISIBLE

                        Toast.makeText(
                            this,
                            "✓ ArUco marker detected!\nYou can now measure tree dimensions.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        binding.tvMarkerStatus.text = "✗ No Marker Found"
                        binding.tvMarkerStatus.setTextColor(getColor(R.color.error))
                        binding.tvPixelRatio.visibility = View.GONE

                        binding.measurementToolbar.visibility = View.GONE

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

            // ✅ IMPROVED: Better image capture settings
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

    /**
     * ✅ ENHANCED: Added loading state
     */
    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        // Show loading state
        binding.btnCapture.isEnabled = false
        binding.btnCapture.text = "⏳"

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
                    // Reset button state on error
                    binding.btnCapture.isEnabled = true
                    binding.btnCapture.text = "📷"

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

    /**
     * ✅ FIXED: Using direct binding reference + memory optimization
     */
    private fun displayCapturedImage(imageFile: File) {
        // Recycle old bitmap to prevent memory leaks
        capturedBitmap?.recycle()

        // Load with scaling to prevent OOM
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)

        // Calculate scaling factor
        val maxSize = 2048
        val scale = Math.max(
            options.outWidth / maxSize,
            options.outHeight / maxSize
        ).coerceAtLeast(1)

        // Load scaled bitmap
        options.inJustDecodeBounds = false
        options.inSampleSize = scale

        capturedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
        binding.ivMeasurementImage.setImageBitmap(capturedBitmap)

        Log.d(TAG, "Image loaded: ${capturedBitmap?.width}x${capturedBitmap?.height} (scale: $scale)")
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

    /**
     * ✅ ADDED: Resource cleanup to prevent memory leaks
     */
    override fun onDestroy() {
        super.onDestroy()

        // Release camera resources
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll()
            Log.d(TAG, "Camera resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding camera: ${e.message}")
        }

        // Release bitmaps to prevent memory leaks
        capturedBitmap?.recycle()
        capturedBitmap = null

        // Clear ArUco helper
        arucoHelper = null
    }
}