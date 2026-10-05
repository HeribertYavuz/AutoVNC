package com.deku.autovnc.vnc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class VncClient(
    val host: String,
    val port: Int,
    val password: String = "",
    val fps: Int = 30,
    val colorDepth: Int = 32,
    private val listener: Listener
) {
    companion object {
        private const val TAG = "VncClient"
        private const val ENCODING_RAW = 0
        private const val ENCODING_COPYRECT = 1
        private const val ENCODING_DESKTOP_SIZE = -223
    }

    interface Listener {
        fun onConnecting()
        fun onConnected(desktopName: String, width: Int, height: Int)
        fun onFrameUpdated()
        fun onResolutionChanged(newWidth: Int, newHeight: Int)
        fun onDisconnected(reason: String?)
        fun onError(error: Throwable)
        fun onBell() {}
        fun onClipboardReceived(text: String) {}
    }

    private var socket: Socket? = null
    private var inStream: DataInputStream? = null
    private var outStream: DataOutputStream? = null
    private val writeLock = Any()

    val isConnected = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)

    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var desktopName: String = ""
        private set

    val bitmapLock = Any()
    var framebuffer: Bitmap? = null
        private set

    // Reusable buffers to minimize GC allocations during streaming
    private var rawPixelBytes = ByteArray(0)
    private var pixelIntBuffer = IntArray(0)
    private var lastFrameTime = 0L

    fun start() {
        if (isRunning.getAndSet(true)) return
        Thread({ runConnection() }, "VncClient-Thread").start()
    }

    fun stop(reason: String? = null) {
        if (!isRunning.getAndSet(false)) return
        isConnected.set(false)
        try {
            socket?.close()
        } catch (_: Exception) {}
        listener.onDisconnected(reason)
    }

    private fun runConnection() {
        listener.onConnecting()
        var errorOccurred = false
        try {
            val s = Socket()
            s.tcpNoDelay = true
            s.soTimeout = 15000
            Log.d(TAG, "Connecting to $host:$port...")
            s.connect(InetSocketAddress(host, port), 8000)
            socket = s

            val input = DataInputStream(BufferedInputStream(s.getInputStream(), 65536))
            val output = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 16384))
            inStream = input
            outStream = output

            // 1. Protocol Version Handshake
            val versionBuf = ByteArray(12)
            input.readFully(versionBuf)
            val serverVersion = String(versionBuf, Charsets.US_ASCII).trim()
            Log.d(TAG, "Server version: $serverVersion")

            val clientVersionStr = when {
                serverVersion.startsWith("RFB 003.003") -> "RFB 003.003\n"
                serverVersion.startsWith("RFB 003.007") -> "RFB 003.007\n"
                else -> "RFB 003.008\n"
            }
            output.write(clientVersionStr.toByteArray(Charsets.US_ASCII))
            output.flush()

            // 2. Security Handshake
            negotiateSecurity(input, output, serverVersion)

            // 3. ClientInit
            output.writeByte(1) // Shared flag: 1 = allow other clients to stay connected
            output.flush()

            // 4. ServerInit
            width = input.readUnsignedShort()
            height = input.readUnsignedShort()

            // Pixel format (16 bytes)
            val pixelFormatBuf = ByteArray(16)
            input.readFully(pixelFormatBuf)

            val nameLength = input.readInt()
            val nameBytes = ByteArray(nameLength)
            input.readFully(nameBytes)
            desktopName = String(nameBytes, Charsets.UTF_8)
            Log.d(TAG, "Connected to Desktop: $desktopName (${width}x${height})")

            // Allocate initial Framebuffer Bitmap
            synchronized(bitmapLock) {
                framebuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }

            // 5. Configure Client: Request 32-bit little-endian RGBA format
            sendSetPixelFormat()

            // 6. Set Encodings: Raw, CopyRect, DesktopSize
            sendSetEncodings()

            isConnected.set(true)
            // Disable socket read timeout for streaming
            s.soTimeout = 0
            listener.onConnected(desktopName, width, height)

            // 7. Request initial full update
            requestUpdate(incremental = false, 0, 0, width, height)

            // 8. Main Message Loop
            while (isRunning.get() && !s.isClosed) {
                val msgType = input.readUnsignedByte()
                when (msgType) {
                    0 -> handleFramebufferUpdate(input)
                    1 -> handleSetColourMapEntries(input)
                    2 -> listener.onBell()
                    3 -> handleServerCutText(input)
                    else -> {
                        Log.w(TAG, "Ignored unknown server message type: $msgType")
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                errorOccurred = true
                Log.e(TAG, "VNC connection error", e)
                listener.onError(e)
            }
        } finally {
            isRunning.set(false)
            isConnected.set(false)
            try {
                socket?.close()
            } catch (_: Exception) {}
            if (!errorOccurred) {
                listener.onDisconnected("Bağlantı Sonlandı")
            }
        }
    }

    private fun negotiateSecurity(input: DataInputStream, output: DataOutputStream, serverVersion: String) {
        if (serverVersion.startsWith("RFB 003.003")) {
            val secType = input.readInt()
            if (secType == 0) {
                val len = input.readInt()
                val err = ByteArray(len)
                input.readFully(err)
                throw IOException("Server rejected connection: " + String(err, Charsets.US_ASCII))
            }
            handleSecurityType(secType, input, output, isVersion38 = false)
        } else {
            // RFB 3.7 or 3.8
            val numTypes = input.readUnsignedByte()
            if (numTypes == 0) {
                val len = input.readInt()
                val err = ByteArray(len)
                input.readFully(err)
                throw IOException("Security negotiation failed: " + String(err, Charsets.US_ASCII))
            }
            val types = ByteArray(numTypes)
            input.readFully(types)

            val typeList = types.map { it.toInt() and 0xFF }
            Log.d(TAG, "Server supported security types: $typeList")

            val selectedType = when {
                typeList.contains(2) && password.isNotEmpty() -> 2 // VNC Auth
                typeList.contains(1) -> 1                          // None
                typeList.contains(2) -> 2                          // VNC Auth
                else -> throw IOException("Desteklenmeyen güvenlik türleri: $typeList")
            }

            output.writeByte(selectedType)
            output.flush()

            val isVersion38 = !serverVersion.startsWith("RFB 003.007")
            handleSecurityType(selectedType, input, output, isVersion38)
        }
    }

    private fun handleSecurityType(
        type: Int,
        input: DataInputStream,
        output: DataOutputStream,
        isVersion38: Boolean
    ) {
        when (type) {
            1 -> {
                // None
                if (isVersion38) {
                    val result = input.readInt()
                    if (result != 0) {
                        readAndThrowSecurityError(input)
                    }
                }
            }
            2 -> {
                // VNC Authentication
                val challenge = ByteArray(16)
                input.readFully(challenge)
                val response = VncDes.encryptChallenge(challenge, password)
                output.write(response)
                output.flush()

                val result = input.readInt()
                if (result != 0) {
                    readAndThrowSecurityError(input)
                }
            }
            else -> throw IOException("Desteklenmeyen güvenlik türü: $type")
        }
    }

    private fun readAndThrowSecurityError(input: DataInputStream) {
        val len = try { input.readInt() } catch (_: Exception) { 0 }
        val reason = if (len in 1..4096) {
            val b = ByteArray(len)
            input.readFully(b)
            String(b, Charsets.UTF_8)
        } else {
            "Kimlik doğrulama başarısız (Hatalı parola)"
        }
        throw IOException(reason)
    }

    private fun sendSetPixelFormat() {
        val out = outStream ?: return
        synchronized(writeLock) {
            out.writeByte(0) // Message type 0: SetPixelFormat
            out.writeByte(0) // Pad 1
            out.writeByte(0) // Pad 2
            out.writeByte(0) // Pad 3

            if (colorDepth == 16) {
                // 16-bit RGB565 (50% bandwidth reduction for lower bitrate)
                out.writeByte(16) // bits-per-pixel
                out.writeByte(16) // depth
                out.writeByte(0)  // big-endian-flag (0 = little-endian)
                out.writeByte(1)  // true-colour-flag
                out.writeShort(31) // red-max (5 bits)
                out.writeShort(63) // green-max (6 bits)
                out.writeShort(31) // blue-max (5 bits)
                out.writeByte(11)  // red-shift
                out.writeByte(5)   // green-shift
                out.writeByte(0)   // blue-shift
                out.writeByte(0)   // pad 1
                out.writeByte(0)   // pad 2
                out.writeByte(0)   // pad 3
            } else {
                // 32-bit ARGB (24-bit true-colour)
                out.writeByte(32) // bits-per-pixel
                out.writeByte(24) // depth
                out.writeByte(0)  // big-endian-flag (0 = little-endian)
                out.writeByte(1)  // true-colour-flag
                out.writeShort(255) // red-max
                out.writeShort(255) // green-max
                out.writeShort(255) // blue-max
                out.writeByte(16)   // red-shift
                out.writeByte(8)    // green-shift
                out.writeByte(0)    // blue-shift
                out.writeByte(0)    // pad 1
                out.writeByte(0)    // pad 2
                out.writeByte(0)    // pad 3
            }
            out.flush()
        }
    }

    private fun sendSetEncodings() {
        val out = outStream ?: return
        synchronized(writeLock) {
            out.writeByte(2) // Message type 2: SetEncodings
            out.writeByte(0) // Padding
            out.writeShort(3) // Number of encodings

            out.writeInt(ENCODING_RAW)
            out.writeInt(ENCODING_COPYRECT)
            out.writeInt(ENCODING_DESKTOP_SIZE)
            out.flush()
        }
    }

    fun requestUpdate(incremental: Boolean, x: Int, y: Int, w: Int, h: Int) {
        val out = outStream ?: return
        if (!isConnected.get()) return
        try {
            synchronized(writeLock) {
                out.writeByte(3) // Message type 3: FramebufferUpdateRequest
                out.writeByte(if (incremental) 1 else 0)
                out.writeShort(x)
                out.writeShort(y)
                out.writeShort(w)
                out.writeShort(h)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request update", e)
        }
    }

    fun requestFullRefresh() {
        requestUpdate(incremental = false, 0, 0, width, height)
    }

    private fun handleFramebufferUpdate(input: DataInputStream) {
        input.readByte() // padding byte 0
        val numRectangles = input.readUnsignedShort()

        for (i in 0 until numRectangles) {
            val rx = input.readUnsignedShort()
            val ry = input.readUnsignedShort()
            val rw = input.readUnsignedShort()
            val rh = input.readUnsignedShort()
            val encoding = input.readInt()

            when (encoding) {
                ENCODING_RAW -> {
                    val pixelCount = rw * rh
                    val bytesPerPixel = if (colorDepth == 16) 2 else 4
                    val byteCount = pixelCount * bytesPerPixel

                    if (rawPixelBytes.size < byteCount) {
                        rawPixelBytes = ByteArray(byteCount)
                    }
                    if (pixelIntBuffer.size < pixelCount) {
                        pixelIntBuffer = IntArray(pixelCount)
                    }

                    input.readFully(rawPixelBytes, 0, byteCount)

                    if (colorDepth == 16) {
                        // Decode RGB565 little-endian (2 bytes per pixel)
                        var byteIdx = 0
                        for (p in 0 until pixelCount) {
                            val b0 = rawPixelBytes[byteIdx].toInt() and 0xFF
                            val b1 = rawPixelBytes[byteIdx + 1].toInt() and 0xFF
                            val rgb565 = b0 or (b1 shl 8)
                            val r5 = (rgb565 shr 11) and 0x1F
                            val g6 = (rgb565 shr 5) and 0x3F
                            val b5 = rgb565 and 0x1F
                            val r8 = (r5 * 255 + 15) / 31
                            val g8 = (g6 * 255 + 31) / 63
                            val b8 = (b5 * 255 + 15) / 31
                            pixelIntBuffer[p] = -0x1000000 or (r8 shl 16) or (g8 shl 8) or b8
                            byteIdx += 2
                        }
                    } else {
                        // Convert little-endian 32-bit BGRA to Android ARGB_8888 (0xAARRGGBB)
                        var byteIdx = 0
                        for (p in 0 until pixelCount) {
                            val b = rawPixelBytes[byteIdx].toInt() and 0xFF
                            val g = rawPixelBytes[byteIdx + 1].toInt() and 0xFF
                            val r = rawPixelBytes[byteIdx + 2].toInt() and 0xFF
                            pixelIntBuffer[p] = -0x1000000 or (r shl 16) or (g shl 8) or b
                            byteIdx += 4
                        }
                    }

                    synchronized(bitmapLock) {
                        framebuffer?.setPixels(pixelIntBuffer, 0, rw, rx, ry, rw, rh)
                    }
                }
                ENCODING_COPYRECT -> {
                    val srcX = input.readUnsignedShort()
                    val srcY = input.readUnsignedShort()
                    synchronized(bitmapLock) {
                        framebuffer?.let { bmp ->
                            val copy = Bitmap.createBitmap(bmp, srcX, srcY, rw, rh)
                            val canvas = Canvas(bmp)
                            canvas.drawBitmap(copy, rx.toFloat(), ry.toFloat(), null)
                            copy.recycle()
                        }
                    }
                }
                ENCODING_DESKTOP_SIZE -> {
                    Log.i(TAG, "Desktop size changed to ${rw}x${rh}")
                    width = rw
                    height = rh
                    synchronized(bitmapLock) {
                        framebuffer?.recycle()
                        framebuffer = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                    }
                    listener.onResolutionChanged(rw, rh)
                }
                else -> {
                    Log.w(TAG, "Unhandled encoding: $encoding, dropping connection")
                    throw IOException("Desteklenmeyen kodlama (Encoding): $encoding")
                }
            }
        }

        // Notify frame updated
        listener.onFrameUpdated()

        // Throttling to enforce target FPS (and cap bitrate accordingly)
        val intervalMs = (1000L / fps.coerceIn(10, 60))
        val now = System.currentTimeMillis()
        val elapsed = now - lastFrameTime
        val delayNeeded = intervalMs - elapsed
        if (delayNeeded > 2) {
            try {
                Thread.sleep(delayNeeded)
            } catch (_: InterruptedException) {}
        }
        lastFrameTime = System.currentTimeMillis()

        // Pipeline the next incremental update request
        requestUpdate(incremental = true, 0, 0, width, height)
    }

    private fun handleSetColourMapEntries(input: DataInputStream) {
        input.readByte() // padding byte
        input.readUnsignedShort() // firstColour
        val numColours = input.readUnsignedShort()
        val bytesToSkip = numColours * 6
        val discard = ByteArray(minOf(bytesToSkip, 4096))
        var remaining = bytesToSkip
        while (remaining > 0) {
            val toRead = minOf(remaining, discard.size)
            input.readFully(discard, 0, toRead)
            remaining -= toRead
        }
    }

    private fun handleServerCutText(input: DataInputStream) {
        val pad = ByteArray(3)
        input.readFully(pad) // 3 bytes padding
        val length = input.readInt()
        if (length in 1..1048576) {
            val textBytes = ByteArray(length)
            input.readFully(textBytes)
            val text = String(textBytes, Charsets.ISO_8859_1)
            Log.d(TAG, "Server clipboard: $text")
            listener.onClipboardReceived(text)
        } else if (length > 1048576) {
            var remaining = length
            val discard = ByteArray(8192)
            while (remaining > 0) {
                val toRead = minOf(remaining, discard.size)
                input.readFully(discard, 0, toRead)
                remaining -= toRead
            }
        }
    }

    // --- Input Injection Methods ---

    fun sendPointerEvent(x: Int, y: Int, buttonMask: Int) {
        val out = outStream ?: return
        if (!isConnected.get()) return
        val clampedX = x.coerceIn(0, (width - 1).coerceAtLeast(0))
        val clampedY = y.coerceIn(0, (height - 1).coerceAtLeast(0))
        try {
            synchronized(writeLock) {
                out.writeByte(5) // Message type 5: PointerEvent
                out.writeByte(buttonMask)
                out.writeShort(clampedX)
                out.writeShort(clampedY)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send pointer event", e)
        }
    }

    fun sendClick(x: Int, y: Int, buttonMask: Int = 1) {
        sendPointerEvent(x, y, buttonMask)
        try { Thread.sleep(20) } catch (_: Exception) {}
        sendPointerEvent(x, y, 0)
    }

    fun sendKeyEvent(keySym: Int, isDown: Boolean) {
        val out = outStream ?: return
        if (!isConnected.get() || keySym == 0) return
        try {
            synchronized(writeLock) {
                out.writeByte(4) // Message type 4: KeyEvent
                out.writeByte(if (isDown) 1 else 0)
                out.writeShort(0) // padding
                out.writeInt(keySym)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send key event", e)
        }
    }

    fun sendKeyPress(keySym: Int) {
        sendKeyEvent(keySym, isDown = true)
        try { Thread.sleep(15) } catch (_: Exception) {}
        sendKeyEvent(keySym, isDown = false)
    }

    fun sendText(text: String) {
        for (char in text) {
            val keySym = char.code
            sendKeyPress(keySym)
        }
    }

    fun sendCtrlAltDel() {
        sendKeyEvent(VncKeys.KEY_CONTROL_L, true)
        sendKeyEvent(VncKeys.KEY_ALT_L, true)
        sendKeyEvent(VncKeys.KEY_DELETE, true)
        try { Thread.sleep(30) } catch (_: Exception) {}
        sendKeyEvent(VncKeys.KEY_DELETE, false)
        sendKeyEvent(VncKeys.KEY_ALT_L, false)
        sendKeyEvent(VncKeys.KEY_CONTROL_L, false)
    }
}
