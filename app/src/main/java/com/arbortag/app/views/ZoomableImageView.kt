package com.arbortag.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

/**
 * Custom ImageView with pinch-zoom, pan, and measurement point marking capabilities
 * Designed for precise tree measurement marking on tablets and phones
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // Matrix for image transformations
    private val matrix = Matrix()
    private val savedMatrix = Matrix()

    // Scale limits
    private val MIN_SCALE = 1f
    private val MAX_SCALE = 5f
    private var currentScale = 1f

    // Pan translation
    private var translateX = 0f
    private var translateY = 0f

    // Touch handling
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    // Measurement points
    private val measurementPoints = mutableListOf<PointF>()
    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }

    // State
    private var isInMeasurementMode = false
    private var measurementCallback: ((PointF) -> Unit)? = null

    init {
        scaleType = ScaleType.MATRIX

        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }

    /**
     * Enable measurement mode - points will be captured on touch
     */
    fun enableMeasurementMode(callback: (PointF) -> Unit) {
        isInMeasurementMode = true
        measurementCallback = callback
    }

    /**
     * Disable measurement mode
     */
    fun disableMeasurementMode() {
        isInMeasurementMode = false
        measurementCallback = null
    }

    /**
     * Add a measurement point
     */
    fun addMeasurementPoint(point: PointF) {
        measurementPoints.add(point)
        invalidate()
    }

    /**
     * Clear all measurement points
     */
    fun clearMeasurementPoints() {
        measurementPoints.clear()
        invalidate()
    }

    /**
     * Get last measurement point
     */
    fun getLastPoint(): PointF? = measurementPoints.lastOrNull()

    /**
     * Remove last measurement point
     */
    fun removeLastPoint() {
        if (measurementPoints.isNotEmpty()) {
            measurementPoints.removeLast()
            invalidate()
        }
    }

    /**
     * Get all measurement points
     */
    fun getMeasurementPoints(): List<PointF> = measurementPoints.toList()

    /**
     * Reset zoom and pan to original state
     */
    fun resetTransform() {
        currentScale = 1f
        translateX = 0f
        translateY = 0f
        matrix.reset()
        imageMatrix = matrix
        invalidate()
    }

    /**
     * Convert screen coordinates to image coordinates
     */
    fun screenToImageCoordinates(screenX: Float, screenY: Float): PointF {
        val values = FloatArray(9)
        matrix.getValues(values)

        val imageX = (screenX - values[Matrix.MTRANS_X]) / values[Matrix.MSCALE_X]
        val imageY = (screenY - values[Matrix.MTRANS_Y]) / values[Matrix.MSCALE_Y]

        return PointF(imageX, imageY)
    }

    /**
     * Convert image coordinates to screen coordinates
     */
    fun imageToScreenCoordinates(imageX: Float, imageY: Float): PointF {
        val values = FloatArray(9)
        matrix.getValues(values)

        val screenX = imageX * values[Matrix.MSCALE_X] + values[Matrix.MTRANS_X]
        val screenY = imageY * values[Matrix.MSCALE_Y] + values[Matrix.MTRANS_Y]

        return PointF(screenX, screenY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled

        // Handle measurement point tapping
        if (isInMeasurementMode && event.action == MotionEvent.ACTION_UP) {
            val imagePoint = screenToImageCoordinates(event.x, event.y)
            measurementCallback?.invoke(imagePoint)
            return true
        }

        return handled || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw measurement points and lines
        if (measurementPoints.isNotEmpty()) {
            // Draw lines between consecutive points
            for (i in 0 until measurementPoints.size - 1) {
                val p1 = imageToScreenCoordinates(measurementPoints[i].x, measurementPoints[i].y)
                val p2 = imageToScreenCoordinates(measurementPoints[i + 1].x, measurementPoints[i + 1].y)
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)

                // Draw distance label at midpoint
                val midX = (p1.x + p2.x) / 2
                val midY = (p1.y + p2.y) / 2
                val distance = calculateDistance(measurementPoints[i], measurementPoints[i + 1])
                canvas.drawText(String.format("%.2f m", distance), midX, midY - 10, textPaint)
            }

            // Draw points
            measurementPoints.forEachIndexed { index, point ->
                val screenPoint = imageToScreenCoordinates(point.x, point.y)

                // Draw outer circle (white border)
                val outerPaint = Paint(pointPaint).apply {
                    color = Color.WHITE
                }
                canvas.drawCircle(screenPoint.x, screenPoint.y, 22f, outerPaint)

                // Draw inner circle (red)
                canvas.drawCircle(screenPoint.x, screenPoint.y, 18f, pointPaint)

                // Draw point number
                val numberPaint = Paint(textPaint).apply {
                    textSize = 30f
                    color = Color.WHITE
                }
                canvas.drawText("${index + 1}", screenPoint.x, screenPoint.y + 10, numberPaint)
            }
        }
    }

    /**
     * Calculate distance between two points (placeholder - actual calculation uses calibration)
     */
    private fun calculateDistance(p1: PointF, p2: PointF): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Scale gesture listener
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newScale = currentScale * scaleFactor

            // Limit scale
            if (newScale in MIN_SCALE..MAX_SCALE) {
                currentScale = newScale

                // Scale around focal point
                matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)

                // Constrain translation
                constrainTranslation()

                imageMatrix = matrix
                invalidate()
            }

            return true
        }
    }

    /**
     * Gesture listener for scrolling and double-tap
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (currentScale > 1f) {
                matrix.postTranslate(-distanceX, -distanceY)
                constrainTranslation()
                imageMatrix = matrix
                invalidate()
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > 1f) {
                // Zoom out
                resetTransform()
            } else {
                // Zoom in to 2x around tap point
                currentScale = 2f
                matrix.setScale(2f, 2f, e.x, e.y)
                constrainTranslation()
                imageMatrix = matrix
                invalidate()
            }
            return true
        }
    }

    /**
     * Constrain translation to keep image within bounds
     */
    private fun constrainTranslation() {
        val drawable = drawable ?: return

        val values = FloatArray(9)
        matrix.getValues(values)

        val imageWidth = drawable.intrinsicWidth * values[Matrix.MSCALE_X]
        val imageHeight = drawable.intrinsicHeight * values[Matrix.MSCALE_Y]

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Calculate max translation
        val maxTransX = max(0f, (imageWidth - viewWidth) / 2)
        val maxTransY = max(0f, (imageHeight - viewHeight) / 2)

        // Constrain X
        var transX = values[Matrix.MTRANS_X]
        if (imageWidth > viewWidth) {
            transX = min(maxTransX, max(-maxTransX, transX))
        } else {
            transX = (viewWidth - imageWidth) / 2
        }

        // Constrain Y
        var transY = values[Matrix.MTRANS_Y]
        if (imageHeight > viewHeight) {
            transY = min(maxTransY, max(-maxTransY, transY))
        } else {
            transY = (viewHeight - imageHeight) / 2
        }

        values[Matrix.MTRANS_X] = transX
        values[Matrix.MTRANS_Y] = transY
        matrix.setValues(values)
    }

    /**
     * Get current zoom level
     */
    fun getCurrentScale(): Float = currentScale

    /**
     * Check if image is zoomed
     */
    fun isZoomed(): Boolean = currentScale > 1f
}