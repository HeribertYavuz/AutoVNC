package com.deku.autovnc.car

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.deku.autovnc.model.ScaleMode
import com.deku.autovnc.model.VncServerProfile
import com.deku.autovnc.vnc.ConnectionStatus
import com.deku.autovnc.vnc.VncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

enum class ClickMode(val title: String) {
    LEFT("Sol Tık"),
    RIGHT("Sağ Tık"),
    DOUBLE("Çift Tık");

    fun next(): ClickMode {
        val vals = values()
        return vals[(ordinal + 1) % vals.size]
    }
}

class CarVncScreen(
    carContext: CarContext,
    private val profile: VncServerProfile
) : Screen(carContext), SurfaceCallback {

    companion object {
        private const val TAG = "CarVncScreen"
    }

    private var currentSurface: Surface? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var surfaceDpi: Int = 0

    private var scaleMode = profile.scaleMode
    private var clickMode = ClickMode.LEFT
    private var userZoom: Float = 1.0f
    private var panX: Float = 0f
    private var panY: Float = 0f

    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val matrixLock = Any()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val overlayPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
    }

    private var renderJob: Job? = null
    private var observeJob: Job? = null
    private val isDirty = AtomicBoolean(true)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                VncState.initSettings(carContext)
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(this@CarVncScreen)

                // Start rendering loop
                startRenderingLoop()

                // Observe frame updates from VNC Client
                observeJob = VncState.frameUpdateTrigger
                    .onEach {
                        isDirty.set(true)
                    }
                    .launchIn(lifecycleScope)

                // Observe hideCarControls toggle from phone
                VncState.hideCarControls
                    .onEach {
                        invalidate()
                    }
                    .launchIn(lifecycleScope)

                // Observe status changes
                VncState.status
                    .onEach { status ->
                        if (status == ConnectionStatus.DISCONNECTED && screenManager.top is CarVncScreen) {
                            try {
                                screenManager.pop()
                            } catch (_: Exception) {}
                        }
                        isDirty.set(true)
                    }
                    .launchIn(lifecycleScope)
            }

            override fun onPause(owner: LifecycleOwner) {
                stopRenderingLoop()
                observeJob?.cancel()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                currentSurface = null
            }
        })
    }

    override fun onGetTemplate(): Template {
        val actionStripBuilder = ActionStrip.Builder()

        // When user hides car controls from phone, only provide a single tiny exit button
        if (VncState.hideCarControls.value) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle("✕")
                    .setOnClickListener {
                        VncState.disconnect(carContext)
                        screenManager.pop()
                    }
                    .build()
            )
        } else {
            // Full control buttons (Exit, Scale, Click mode, Special keys)
            actionStripBuilder
                .addAction(
                    Action.Builder()
                        .setTitle("Çıkış")
                        .setOnClickListener {
                            VncState.disconnect(carContext)
                            screenManager.pop()
                        }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle(scaleMode.name)
                        .setOnClickListener {
                            scaleMode = scaleMode.next()
                            userZoom = 1.0f
                            panX = 0f
                            panY = 0f
                            isDirty.set(true)
                            invalidate()
                            CarToast.makeText(carContext, "Ölçek: ${scaleMode.title}", CarToast.LENGTH_SHORT).show()
                        }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle(clickMode.title)
                        .setOnClickListener {
                            clickMode = clickMode.next()
                            invalidate()
                            CarToast.makeText(carContext, "Tıklama: ${clickMode.title}", CarToast.LENGTH_SHORT).show()
                        }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle("Tuşlar")
                        .setOnClickListener {
                            screenManager.push(CarSpecialKeysScreen(carContext))
                        }
                        .build()
                )
        }

        return NavigationTemplate.Builder()
            .setActionStrip(actionStripBuilder.build())
            .build()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        if (!surface.isValid) return

        currentSurface = surface
        surfaceWidth = surfaceContainer.width
        surfaceHeight = surfaceContainer.height
        surfaceDpi = surfaceContainer.dpi

        isDirty.set(true)
        Log.d(TAG, "onSurfaceAvailable: ${surfaceWidth}x$surfaceHeight, dpi=$surfaceDpi")
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        currentSurface = null
        Log.d(TAG, "onSurfaceDestroyed")
    }

    override fun onClick(x: Float, y: Float) {
        val client = VncState.activeClient ?: return
        if (!client.isConnected.get()) return

        val pts = floatArrayOf(x, y)
        synchronized(matrixLock) {
            inverseMatrix.mapPoints(pts)
        }

        val remoteX = pts[0].toInt().coerceIn(0, max(0, client.width - 1))
        val remoteY = pts[1].toInt().coerceIn(0, max(0, client.height - 1))

        when (clickMode) {
            ClickMode.LEFT -> {
                client.sendClick(remoteX, remoteY, 1)
            }
            ClickMode.RIGHT -> {
                client.sendClick(remoteX, remoteY, 4)
            }
            ClickMode.DOUBLE -> {
                client.sendClick(remoteX, remoteY, 1)
                try { Thread.sleep(40) } catch (_: Exception) {}
                client.sendClick(remoteX, remoteY, 1)
            }
        }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        val client = VncState.activeClient ?: return
        if (userZoom > 1.05f || scaleMode == ScaleMode.ORIGINAL) {
            // Pan
            panX -= distanceX
            panY -= distanceY
            isDirty.set(true)
        } else {
            // Mouse wheel scroll event
            val midX = client.width / 2
            val midY = client.height / 2
            if (distanceY > 15f) {
                client.sendClick(midX, midY, 16) // Wheel Down
            } else if (distanceY < -15f) {
                client.sendClick(midX, midY, 8)  // Wheel Up
            }
        }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        userZoom = (userZoom * scaleFactor).coerceIn(0.5f, 4.0f)
        isDirty.set(true)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (userZoom > 1.05f || scaleMode == ScaleMode.ORIGINAL) {
            panX += velocityX * 0.05f
            panY += velocityY * 0.05f
            isDirty.set(true)
        }
    }

    private fun startRenderingLoop() {
        renderJob?.cancel()
        renderJob = lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                if (isDirty.getAndSet(false)) {
                    renderFrame()
                }
                val targetDelay = 1000L / profile.fps.coerceIn(10, 60)
                delay(targetDelay)
            }
        }
    }

    private fun stopRenderingLoop() {
        renderJob?.cancel()
        renderJob = null
    }

    private fun renderFrame() {
        val surface = currentSurface ?: return
        if (!surface.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) return

        val client = VncState.activeClient
        val canvas = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                surface.lockHardwareCanvas()
            } else {
                surface.lockCanvas(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to lock surface canvas", e)
            null
        } ?: return

        try {
            canvas.drawColor(Color.BLACK)

            if (client != null && client.isConnected.get()) {
                val rw = client.width.toFloat()
                val rh = client.height.toFloat()
                val sw = surfaceWidth.toFloat()
                val sh = surfaceHeight.toFloat()

                synchronized(matrixLock) {
                    transformMatrix.reset()

                    when (scaleMode) {
                        ScaleMode.FIT -> {
                            val scale = min(sw / rw, sh / rh) * userZoom
                            val dx = (sw - rw * scale) / 2f + panX
                            val dy = (sh - rh * scale) / 2f + panY
                            transformMatrix.postScale(scale, scale)
                            transformMatrix.postTranslate(dx, dy)
                        }
                        ScaleMode.FILL -> {
                            val sx = (sw / rw) * userZoom
                            val sy = (sh / rh) * userZoom
                            val dx = panX
                            val dy = panY
                            transformMatrix.postScale(sx, sy)
                            transformMatrix.postTranslate(dx, dy)
                        }
                        ScaleMode.ORIGINAL -> {
                            val scale = 1.0f * userZoom
                            val dx = (sw - rw * scale) / 2f + panX
                            val dy = (sh - rh * scale) / 2f + panY
                            transformMatrix.postScale(scale, scale)
                            transformMatrix.postTranslate(dx, dy)
                        }
                    }

                    transformMatrix.invert(inverseMatrix)
                }

                synchronized(client.bitmapLock) {
                    client.framebuffer?.let { bmp ->
                        canvas.drawBitmap(bmp, transformMatrix, bitmapPaint)
                    }
                }
            } else {
                // Not connected / Connecting
                val msg = VncState.statusMessage.value
                val textWidth = overlayPaint.measureText(msg)
                val x = (surfaceWidth - textWidth) / 2f
                val y = surfaceHeight / 2f
                canvas.drawText(msg, x, y, overlayPaint)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering frame", e)
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unlock canvas", e)
            }
        }
    }
}
