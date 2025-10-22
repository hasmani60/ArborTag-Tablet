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
 * ArUco Measurement Helper for OpenCV 4.5.3
 * Detects ArUco markers and enables accurate distance measurements
 *
 * @param markerSizeMeters The physical size of the ArUco marker (side length in meters)
 */
class ArUcoMeasurementHelper(private val markerSizeMeters: Double) {

    private var pixelToMeterRatio: Double = 0.0
    private val TAG = "ArUcoHelper"

    /**
     * Detect ArUco marker in image and calculate pixel-to-meter calibration
     *
     * @param bitmap Input image containing the ArUco marker
     * @return true if marker was detected and calibration successful
     */
    fun detectMarkerAndCalculateRatio(bitmap: Bitmap): Boolean {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

        try {
            // Get ArUco dictionary (DICT_6X6_250: 6x6 bits, markers 0-249)
            val dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_6X6_250)

            // Create detector parameters
            val parameters = DetectorParameters.create()

            // Prepare containers for detection results
            val corners = ArrayList<Mat>()
            val ids = Mat()
            val rejectedImgPoints = ArrayList<Mat>()

            // Detect ArUco markers in the image (OLD API)
            Aruco.detectMarkers(gray, dictionary, corners, ids, parameters, rejectedImgPoints)

            Log.d(TAG, "Detected ${corners.size} ArUco marker(s)")

            if (corners.isNotEmpty() && !ids.empty()) {
                // Get the first detected marker's corners
                val markerCorners = corners[0]
                val markerId = ids.get(0, 0)[0].toInt()

                Log.d(TAG, "Using marker ID: $markerId")

                // Calculate the marker's perimeter in pixels
                val perimeterPixels = calculatePerimeter(markerCorners)

                // Calculate side length (perimeter / 4 for square marker)
                val sideLengthPixels = perimeterPixels / 4.0

                // Calculate pixel-to-meter ratio
                pixelToMeterRatio = sideLengthPixels / markerSizeMeters

                Log.d(TAG, "Calibration successful!")
                Log.d(TAG, "Side length: $sideLengthPixels pixels")
                Log.d(TAG, "Ratio: $pixelToMeterRatio pixels/meter")

                // Clean up
                mat.release()
                gray.release()
                ids.release()
                corners.forEach { it.release() }
                rejectedImgPoints.forEach { it.release() }

                return true
            } else {
                Log.w(TAG, "No ArUco markers detected in image")
            }

            // Clean up if no markers found
            mat.release()
            gray.release()
            ids.release()
            rejectedImgPoints.forEach { it.release() }

            return false

        } catch (e: Exception) {
            Log.e(TAG, "Error during marker detection: ${e.message}", e)
            mat.release()
            gray.release()
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
        return pixelDistance / pixelToMeterRatio
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

            // Draw detected markers with green outlines (OLD API)
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