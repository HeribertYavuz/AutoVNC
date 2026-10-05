package com.deku.autovnc.vnc

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.deku.autovnc.model.VncServerProfile
import com.deku.autovnc.service.VncForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

object VncState {
    private const val TAG = "VncState"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _statusMessage = MutableStateFlow("Bağlantı Yok")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _frameUpdateTrigger = MutableStateFlow(0L)
    val frameUpdateTrigger: StateFlow<Long> = _frameUpdateTrigger.asStateFlow()

    private val _hideCarControls = MutableStateFlow(false)
    val hideCarControls: StateFlow<Boolean> = _hideCarControls.asStateFlow()

    var activeClient: VncClient? = null
        private set

    var activeProfile: VncServerProfile? = null
        private set

    fun initSettings(context: Context) {
        val repo = com.deku.autovnc.data.ServerRepository(context)
        _hideCarControls.value = repo.isCarControlsHidden()
    }

    fun setCarControlsHidden(context: Context, hidden: Boolean) {
        _hideCarControls.value = hidden
        com.deku.autovnc.data.ServerRepository(context).setCarControlsHidden(hidden)
    }

    fun toggleCarControlsHidden(context: Context): Boolean {
        val newVal = !_hideCarControls.value
        setCarControlsHidden(context, newVal)
        return newVal
    }

    fun connect(context: Context, profile: VncServerProfile) {
        disconnect(context)
        activeProfile = profile
        _status.value = ConnectionStatus.CONNECTING
        _statusMessage.value = "${profile.name} bağlanıyor…"

        // Start Foreground Service to keep connection alive
        try {
            val serviceIntent = Intent(context, VncForegroundService::class.java).apply {
                putExtra("server_name", profile.name)
            }
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start foreground service", e)
        }

        val client = VncClient(
            host = profile.host,
            port = profile.port,
            password = profile.password,
            fps = profile.fps,
            colorDepth = profile.colorDepth,
            listener = object : VncClient.Listener {
                override fun onConnecting() {
                    mainHandler.post {
                        _status.value = ConnectionStatus.CONNECTING
                        _statusMessage.value = "${profile.host}:${profile.port} bağlanıyor…"
                    }
                }

                override fun onConnected(desktopName: String, width: Int, height: Int) {
                    mainHandler.post {
                        _status.value = ConnectionStatus.CONNECTED
                        _statusMessage.value = "$desktopName (${width}x$height)"
                    }
                }

                override fun onFrameUpdated() {
                    _frameUpdateTrigger.value = System.currentTimeMillis()
                }

                override fun onResolutionChanged(newWidth: Int, newHeight: Int) {
                    mainHandler.post {
                        _statusMessage.value = "${activeProfile?.name} (${newWidth}x$newHeight)"
                        _frameUpdateTrigger.value = System.currentTimeMillis()
                    }
                }

                override fun onDisconnected(reason: String?) {
                    mainHandler.post {
                        _status.value = ConnectionStatus.DISCONNECTED
                        _statusMessage.value = reason ?: "Bağlantı Kesildi"
                    }
                }

                override fun onError(error: Throwable) {
                    mainHandler.post {
                        _status.value = ConnectionStatus.ERROR
                        _statusMessage.value = "Hata: ${error.localizedMessage ?: "Bağlantı başarısız"}"
                    }
                }
            }
        )

        activeClient = client
        client.start()
    }

    fun disconnect(context: Context? = null) {
        activeClient?.stop()
        activeClient = null
        _status.value = ConnectionStatus.DISCONNECTED
        _statusMessage.value = "Bağlantı Kesildi"

        if (context != null) {
            try {
                val serviceIntent = Intent(context, VncForegroundService::class.java)
                context.stopService(serviceIntent)
            } catch (_: Exception) {}
        }
    }
}
