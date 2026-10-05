package com.deku.autovnc.vnc

import android.view.KeyEvent

object VncKeys {
    const val KEY_BACKSPACE = 0xFF08
    const val KEY_TAB = 0xFF09
    const val KEY_RETURN = 0xFF0D
    const val KEY_ESCAPE = 0xFF1B
    const val KEY_INSERT = 0xFF63
    const val KEY_DELETE = 0xFFFF
    const val KEY_HOME = 0xFF50
    const val KEY_END = 0xFF57
    const val KEY_PAGE_UP = 0xFF55
    const val KEY_PAGE_DOWN = 0xFF56
    const val KEY_LEFT = 0xFF51
    const val KEY_UP = 0xFF52
    const val KEY_RIGHT = 0xFF53
    const val KEY_DOWN = 0xFF54
    const val KEY_F1 = 0xFFBE
    const val KEY_F2 = 0xFFBF
    const val KEY_F3 = 0xFFC0
    const val KEY_F4 = 0xFFC1
    const val KEY_F5 = 0xFFC2
    const val KEY_F6 = 0xFFC3
    const val KEY_F7 = 0xFFC4
    const val KEY_F8 = 0xFFC5
    const val KEY_F9 = 0xFFC6
    const val KEY_F10 = 0xFFC7
    const val KEY_F11 = 0xFFC8
    const val KEY_F12 = 0xFFC9
    const val KEY_SHIFT_L = 0xFFE1
    const val KEY_CONTROL_L = 0xFFE3
    const val KEY_CONTROL_R = 0xFFE4
    const val KEY_ALT_L = 0xFFE9
    const val KEY_SUPER_L = 0xFFEB // Windows key

    fun getKeysymFromAndroidKey(keyCode: Int, event: KeyEvent): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> KEY_BACKSPACE
            KeyEvent.KEYCODE_FORWARD_DEL -> KEY_DELETE
            KeyEvent.KEYCODE_TAB -> KEY_TAB
            KeyEvent.KEYCODE_ENTER -> KEY_RETURN
            KeyEvent.KEYCODE_ESCAPE -> KEY_ESCAPE
            KeyEvent.KEYCODE_DPAD_UP -> KEY_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> KEY_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> KEY_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> KEY_RIGHT
            KeyEvent.KEYCODE_PAGE_UP -> KEY_PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> KEY_PAGE_DOWN
            KeyEvent.KEYCODE_MOVE_HOME -> KEY_HOME
            KeyEvent.KEYCODE_MOVE_END -> KEY_END
            KeyEvent.KEYCODE_F1 -> KEY_F1
            KeyEvent.KEYCODE_F2 -> KEY_F2
            KeyEvent.KEYCODE_F3 -> KEY_F3
            KeyEvent.KEYCODE_F4 -> KEY_F4
            KeyEvent.KEYCODE_F5 -> KEY_F5
            KeyEvent.KEYCODE_F6 -> KEY_F6
            KeyEvent.KEYCODE_F7 -> KEY_F7
            KeyEvent.KEYCODE_F8 -> KEY_F8
            KeyEvent.KEYCODE_F9 -> KEY_F9
            KeyEvent.KEYCODE_F10 -> KEY_F10
            KeyEvent.KEYCODE_F11 -> KEY_F11
            KeyEvent.KEYCODE_F12 -> KEY_F12
            KeyEvent.KEYCODE_CTRL_LEFT -> KEY_CONTROL_L
            KeyEvent.KEYCODE_CTRL_RIGHT -> KEY_CONTROL_R
            KeyEvent.KEYCODE_ALT_LEFT -> KEY_ALT_L
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> KEY_SHIFT_L
            else -> {
                val unicode = event.unicodeChar
                if (unicode in 0x20..0x7E) {
                    unicode
                } else if (unicode > 0) {
                    // Latin or Unicode mapping
                    0x01000000 or unicode
                } else {
                    0
                }
            }
        }
    }
}
