package com.deku.autovnc.vnc

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object VncDes {
    /**
     * VNC Authentication (Security Type 2):
     * The password is truncated or zero-padded to 8 bytes.
     * Each byte in the key has its bits mirrored before DES encryption.
     */
    fun encryptChallenge(challenge: ByteArray, password: String): ByteArray {
        val passBytes = password.toByteArray(Charsets.US_ASCII)
        val key = ByteArray(8)
        for (i in 0 until 8) {
            if (i < passBytes.size) {
                key[i] = reverseBits(passBytes[i])
            } else {
                key[i] = 0
            }
        }
        val secretKey = SecretKeySpec(key, "DES")
        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(challenge)
    }

    private fun reverseBits(b: Byte): Byte {
        var v = b.toInt() and 0xFF
        var r = 0
        for (i in 0 until 8) {
            r = (r shl 1) or (v and 1)
            v = v shr 1
        }
        return r.toByte()
    }
}
