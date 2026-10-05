package com.deku.autovnc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.deku.autovnc.car.ClickMode
import com.deku.autovnc.model.ScaleMode
import com.deku.autovnc.vnc.VncClient
import com.deku.autovnc.vnc.VncState
import kotlin.math.max
import kotlin.math.min

class VncView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val matrixTransform = Matrix()
    private val inverseMatrix = Matrix()

    var scaleMode: ScaleMode = ScaleMode.FIT
        set(value) {
            field = value
            zoom = 1.0f
            panX = 0f
            panY = 0f
            postInvalidate()
        }

    var clickMode: ClickMode = ClickMode.LEFT

    private var zoom: Float = 1.0f
    private var panX: Float = 0f
    private var panY: Float = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(0.5f, 5.0f)
            postInvalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            panX -= distanceX
            panY -= distanceY
            postInvalidate()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val client = VncState.activeClient ?: return false
            val (rx, ry) = mapToRemote(e.x, e.y, client)
            when (clickMode) {
                ClickMode.LEFT -> client.sendClick(rx, ry, 1)
                ClickMode.RIGHT -> client.sendClick(rx, ry, 4)
                ClickMode.DOUBLE -> {
                    client.sendClick(rx, ry, 1)
                    postDelayed({ client.sendClick(rx, ry, 1) }, 50)
                }
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val client = VncState.activeClient ?: return false
            val (rx, ry) = mapToRemote(e.x, e.y, client)
            client.sendClick(rx, ry, 1)
            postDelayed({ client.sendClick(rx, ry, 1) }, 40)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // Long press always triggers right click
            val client = VncState.activeClient ?: return
            val (rx, ry) = mapToRemote(e.x, e.y, client)
            client.sendClick(rx, ry, 4)
        }
    })

    private fun mapToRemote(screenX: Float, screenY: Float, client: VncClient): Pair<Int, Int> {
        val pts = floatArrayOf(screenX, screenY)
        inverseMatrix.mapPoints(pts)
        val rx = pts[0].toInt().coerceIn(0, max(0, client.width - 1))
        val ry = pts[1].toInt().coerceIn(0, max(0, client.height - 1))
        return Pair(rx, ry)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        val client = VncState.activeClient ?: return
        if (!client.isConnected.get()) return

        val rw = client.width.toFloat()
        val rh = client.height.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()

        if (rw <= 0 || rh <= 0 || vw <= 0 || vh <= 0) return

        matrixTransform.reset()
        when (scaleMode) {
            ScaleMode.FIT -> {
                val scale = min(vw / rw, vh / rh) * zoom
                val dx = (vw - rw * scale) / 2f + panX
                val dy = (vh - rh * scale) / 2f + panY
                matrixTransform.postScale(scale, scale)
                matrixTransform.postTranslate(dx, dy)
            }
            ScaleMode.FILL -> {
                val sx = (vw / rw) * zoom
                val sy = (vh / rh) * zoom
                matrixTransform.postScale(sx, sy)
                matrixTransform.postTranslate(panX, panY)
            }
            ScaleMode.ORIGINAL -> {
                val scale = 1.0f * zoom
                val dx = (vw - rw * scale) / 2f + panX
                val dy = (vh - rh * scale) / 2f + panY
                matrixTransform.postScale(scale, scale)
                matrixTransform.postTranslate(dx, dy)
            }
        }

        matrixTransform.invert(inverseMatrix)

        synchronized(client.bitmapLock) {
            client.framebuffer?.let { bmp ->
                canvas.drawBitmap(bmp, matrixTransform, paint)
            }
        }
    }
}
