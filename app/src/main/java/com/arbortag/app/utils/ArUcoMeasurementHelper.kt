package com.arbortag.app.utils

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.aruco.Aruco
import org.opencv.aruco.DetectorParameters
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.Locale
import kotlin.math.sqrt

/**
 * Enhanced ArUco Measurement Helper for OpenCV 4.5.3+
 * Detects ArUco markers and enables accurate distance measurements
 * with improved preprocessing and error handling
 *
 * @param markerSizeMeters The physical size of the ArUco marker (side length in meters)
 */
class ArUcoMeasurementHelper(private val markerSizeMeters: Double) {

    private var pixelToMeterRatio: Double = 0.0
    private val TAG = "ArUcoHelper"

    /**
     * Detect ArUco marker in image and calculate pixel-to-meter calibration
     * ENHANCED with better preprocessing and error handling
     *
     * @param bitmap Input image containing the ArUco marker
     * @return true if marker was detected and calibration successful
     */
    fun detectMarkerAndCalculateRatio(bitmap: Bitmap): Boolean {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Starting ArUco Detection")
        Log.d(TAG, "Image size: ${bitmap.width}x${bitmap.height}")
        Log.d(TAG, "Expected marker size: $markerSizeMeters m")
        Log.d(TAG, "========================================")

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

        // ENHANCEMENT: Improve image quality for detection
        val enhancedGray = Mat()
        Imgproc.equalizeHist(gray, enhancedGray)  // Enhance contrast

        // Optional: Apply Gaussian blur to reduce noise
        val blurred = Mat()
        Imgproc.GaussianBlur(enhancedGray, blurred, Size(5.0, 5.0), 0.0)

        try {
            // Get ArUco dictionary (DICT_6X6_250: 6x6 bits, markers 0-249)
            val dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_6X6_250)
            Log.d(TAG, "✓ Dictionary loaded: DICT_6X6_250")

            // Create detector parameters with TUNED settings
            val parameters = DetectorParameters.create()

            // CRITICAL: Tune these for better detection
            parameters.set_adaptiveThreshWinSizeMin(3)
            parameters.set_adaptiveThreshWinSizeMax(23)
            parameters.set_adaptiveThreshWinSizeStep(10)
            parameters.set_minMarkerPerimeterRate(0.03)  // Min marker size (3% of image)
            parameters.set_maxMarkerPerimeterRate(4.0)   // Max marker size (400% of image)
            parameters.set_polygonalApproxAccuracyRate(0.03)
            parameters.set_minCornerDistanceRate(0.05)
            parameters.set_minDistanceToBorder(3)
            parameters.set_cornerRefinementWinSize(5)
            parameters.set_cornerRefinementMethod(Aruco.CORNER_REFINE_SUBPIX)

            Log.d(TAG, "✓ Detector parameters configured")

            // Prepare containers for detection results
            val corners = ArrayList<Mat>()
            val ids = Mat()
            val rejectedImgPoints = ArrayList<Mat>()

            // Detect ArUco markers in the image
            Log.d(TAG, "Running marker detection...")
            Aruco.detectMarkers(blurred, dictionary, corners, ids, parameters, rejectedImgPoints)

            Log.d(TAG, "Detection complete:")
            Log.d(TAG, "  ✓ Detected: ${corners.size} marker(s)")
            Log.d(TAG, "  ⚠ Rejected: ${rejectedImgPoints.size} candidate(s)")

            if (corners.isEmpty() || ids.empty()) {
                Log.w(TAG, "========================================")
                Log.w(TAG, "✗ NO MARKERS DETECTED")
                Log.w(TAG, "========================================")
                Log.w(TAG, "Troubleshooting checklist:")
                Log.w(TAG, "  □ Is the marker DICT_6X6_250?")
                Log.w(TAG, "  □ Is the marker clearly visible in frame?")
                Log.w(TAG, "  □ Is there good lighting (not too bright/dark)?")
                Log.w(TAG, "  □ Is the image sharp (not blurred)?")
                Log.w(TAG, "  □ Is the marker flat (not curved/bent)?")
                Log.w(TAG, "  □ Is the marker size correct in Settings?")

                // Clean up
                mat.release()
                gray.release()
                enhancedGray.release()
                blurred.release()
                ids.release()
                rejectedImgPoints.forEach { it.release() }

                return false
            }

            // SUCCESS: Marker(s) detected
            Log.d(TAG, "========================================")
            Log.d(TAG, "✓ MARKER(S) FOUND")
            Log.d(TAG, "========================================")

            // Get the first detected marker's corners
            val markerCorners = corners[0]
            val markerId = ids.get(0, 0)[0].toInt()

            Log.d(TAG, "Using marker ID: $markerId")

            // Log corner positions
            for (i in 0 until 4) {
                val x = markerCorners.get(0, i)[0]
                val y = markerCorners.get(0, i)[1]
                Log.d(TAG, "  Corner $i: (${"%.1f".format(x)}, ${"%.1f".format(y)})")
            }

            // Calculate the marker's perimeter in pixels
            val perimeterPixels = calculatePerimeter(markerCorners)
            Log.d(TAG, "Perimeter: ${"%.2f".format(perimeterPixels)} pixels")

            // Calculate side length (perimeter / 4 for square marker)
            val sideLengthPixels = perimeterPixels / 4.0
            Log.d(TAG, "Average side length: ${"%.2f".format(sideLengthPixels)} pixels")

            // Calculate pixel-to-meter ratio
            pixelToMeterRatio = sideLengthPixels / markerSizeMeters

            Log.d(TAG, "========================================")
            Log.d(TAG, "✓ CALIBRATION SUCCESSFUL")
            Log.d(TAG, "========================================")
            Log.d(TAG, "Marker physical size: $markerSizeMeters m")
            Log.d(TAG, "Marker pixel size: ${"%.2f".format(sideLengthPixels)} px")
            Log.d(TAG, "Ratio: ${"%.2f".format(pixelToMeterRatio)} pixels/meter")
            Log.d(TAG, "1 pixel = ${"%.4f".format(1.0 / pixelToMeterRatio)} meters")
            Log.d(TAG, "========================================")

            // Clean up
            mat.release()
            gray.release()
            enhancedGray.release()
            blurred.release()
            ids.release()
            corners.forEach { it.release() }
            rejectedImgPoints.forEach { it.release() }

            return true

        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "✗ NATIVE LIBRARY ERROR")
            Log.e(TAG, "========================================")
            Log.e(TAG, "ArUco native library not loaded!", e)
            Log.e(TAG, "This means OpenCV ArUco module is not properly included")
            Log.e(TAG, "Solution: Check build.gradle.kts OpenCV dependency")

            mat.release()
            gray.release()
            enhancedGray.release()
            blurred.release()
            return false

        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "✗ DETECTION ERROR")
            Log.e(TAG, "========================================")
            Log.e(TAG, "Error during marker detection: ${e.message}", e)

            mat.release()
            gray.release()
            enhancedGray.release()
            blurred.release()
            return false
        }
    }

    /**
     * Calculate the perimeter of a detected marker from its corner points
     *
     * @param corners Mat containing the 4 corner points of the marker
     * @return Total perimeter in pixels
     */
    private fun calculatePerimeter(corners: Mat): Double {
        var perimeter = 0.0

        // ArUco markers have 4 corners, calculate distance between consecutive corners
        for (i in 0 until 4) {
            val p1x = corners.get(0, i)[0]
            val p1y = corners.get(0, i)[1]

            val nextIdx = (i + 1) % 4
            val p2x = corners.get(0, nextIdx)[0]
            val p2y = corners.get(0, nextIdx)[1]

            val dx = p2x - p1x
            val dy = p2y - p1y
            val distance = sqrt(dx * dx + dy * dy)

            perimeter += distance
        }

        return perimeter
    }

    /**
     * Calculate real-world distance between two points in meters
     *
     * @param point1 First point (x, y) in pixels
     * @param point2 Second point (x, y) in pixels
     * @return Distance in meters
     * @throws IllegalStateException if calibration hasn't been performed
     */
    fun calculateDistance(point1: Pair<Float, Float>, point2: Pair<Float, Float>): Double {
        if (pixelToMeterRatio == 0.0) {
            throw IllegalStateException(
                "Helper not calibrated! Call detectMarkerAndCalculateRatio() first."
            )
        }

        val dx = point2.first - point1.first
        val dy = point2.second - point1.second
        val pixelDistance = sqrt((dx * dx + dy * dy).toDouble())

        // Convert pixel distance to meters
        val realDistance = pixelDistance / pixelToMeterRatio

        Log.d(TAG, "Distance calculation:")
        Log.d(TAG, "  Pixel distance: ${"%.2f".format(pixelDistance)} px")
        Log.d(TAG, "  Real distance: ${"%.2f".format(realDistance)} m")

        return realDistance
    }

    /**
     * Draw detected markers on the image with green outlines and IDs
     * Useful for debugging and showing calibration feedback
     *
     * @param bitmap Input image
     * @return New bitmap with markers drawn
     */
    fun drawDetectedMarkers(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

        try {
            val dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_6X6_250)
            val parameters = DetectorParameters.create()

            val corners = ArrayList<Mat>()
            val ids = Mat()
            val rejectedImgPoints = ArrayList<Mat>()

            Aruco.detectMarkers(gray, dictionary, corners, ids, parameters, rejectedImgPoints)

            // Draw detected markers with green outlines
            if (corners.isNotEmpty() && !ids.empty()) {
                Aruco.drawDetectedMarkers(mat, corners, ids, Scalar(0.0, 255.0, 0.0))
                Log.d(TAG, "Drew ${corners.size} marker(s) on image")
            }

            val resultBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, resultBitmap)

            // Clean up
            mat.release()
            gray.release()
            ids.release()
            corners.forEach { it.release() }
            rejectedImgPoints.forEach { it.release() }

            return resultBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Error drawing markers: ${e.message}", e)
            mat.release()
            gray.release()
            return bitmap
        }
    }

    /**
     * Annotate image with measurement line, endpoints, and distance text
     *
     * @param bitmap Original image
     * @param point1 First measurement point (x, y)
     * @param point2 Second measurement point (x, y)
     * @param distanceMeters Calculated distance in meters
     * @return New bitmap with annotations
     */
    fun annotateMeasurement(
        bitmap: Bitmap,
        point1: Pair<Float, Float>,
        point2: Pair<Float, Float>,
        distanceMeters: Double
    ): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val p1 = Point(point1.first.toDouble(), point1.second.toDouble())
        val p2 = Point(point2.first.toDouble(), point2.second.toDouble())

        // Draw measurement line (Green, 5px thick)
        Imgproc.line(mat, p1, p2, Scalar(0.0, 255.0, 0.0), 5)

        // Draw circles at measurement endpoints (Red, filled)
        Imgproc.circle(mat, p1, 15, Scalar(255.0, 0.0, 0.0), -1)
        Imgproc.circle(mat, p2, 15, Scalar(255.0, 0.0, 0.0), -1)

        // Calculate midpoint for text placement
        val midPoint = Point((p1.x + p2.x) / 2, (p1.y + p2.y) / 2 - 20)

        // Format distance text
        val text = String.format(Locale.getDefault(), "%.2f m", distanceMeters)

        // Draw distance text (Green, 2.0 scale, 4px thick)
        Imgproc.putText(
            mat,
            text,
            midPoint,
            Imgproc.FONT_HERSHEY_SIMPLEX,
            2.0,
            Scalar(0.0, 255.0, 0.0),
            4
        )

        val resultBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, resultBitmap)

        mat.release()

        return resultBitmap
    }

    /**
     * Check if the helper has been calibrated
     *
     * @return true if calibrated and ready for measurements
     */
    fun isCalibrated(): Boolean = pixelToMeterRatio > 0.0

    /**
     * Get the current pixel-to-meter conversion ratio
     *
     * @return Ratio of pixels per meter, or 0.0 if not calibrated
     */
    fun getPixelToMeterRatio(): Double = pixelToMeterRatio

    /**
     * Reset calibration (useful for recalibrating with different marker or distance)
     */
    fun resetCalibration() {
        pixelToMeterRatio = 0.0
        Log.d(TAG, "Calibration reset")
    }
}