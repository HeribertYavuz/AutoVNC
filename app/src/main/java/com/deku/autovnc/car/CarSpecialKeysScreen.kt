package com.deku.autovnc.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.deku.autovnc.vnc.VncKeys
import com.deku.autovnc.vnc.VncState

class CarSpecialKeysScreen(carContext: CarContext) : Screen(carContext) {

    private val specialKeys = listOf(
        "Enter (Giriş)" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_RETURN)
            showToast("Enter gönderildi")
        },
        "Escape (Çıkış)" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_ESCAPE)
            showToast("Esc gönderildi")
        },
        "Ctrl + Alt + Del" to {
            VncState.activeClient?.sendCtrlAltDel()
            showToast("Ctrl+Alt+Del gönderildi")
        },
        "Windows / Super Tuşu" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_SUPER_L)
            showToast("Windows tuşu gönderildi")
        },
        "Tab" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_TAB)
            showToast("Tab gönderildi")
        },
        "Boşluk (Space)" to {
            VncState.activeClient?.sendKeyPress(0x0020)
            showToast("Boşluk tuşu gönderildi")
        },
        "Backspace (Geri Sil)" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_BACKSPACE)
            showToast("Backspace gönderildi")
        },
        "Alt + Tab" to {
            val client = VncState.activeClient
            client?.sendKeyEvent(VncKeys.KEY_ALT_L, true)
            client?.sendKeyEvent(VncKeys.KEY_TAB, true)
            try { Thread.sleep(20) } catch (_: Exception) {}
            client?.sendKeyEvent(VncKeys.KEY_TAB, false)
            client?.sendKeyEvent(VncKeys.KEY_ALT_L, false)
            showToast("Alt+Tab gönderildi")
        },
        "F5 (Yenile)" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_F5)
            showToast("F5 gönderildi")
        },
        "Yukarı Ok (▲)" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_UP)
            showToast("Yukarı Ok gönderildi")
        },
        "Aşağı Ok (▼)" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_DOWN)
            showToast("Aşağı Ok gönderildi")
        },
        "Sol Ok (◄)" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_LEFT)
            showToast("Sol Ok gönderildi")
        },
        "Sağ Ok (►)" to {
            VncState.activeClient?.sendKeyPress(VncKeys.KEY_RIGHT)
            showToast("Sağ Ok gönderildi")
        }
    )

    private fun showToast(msg: String) {
        CarToast.makeText(carContext, msg, CarToast.LENGTH_SHORT).show()
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        for ((label, action) in specialKeys) {
            val row = Row.Builder()
                .setTitle(label)
                .setOnClickListener {
                    action()
                }
                .build()
            listBuilder.addItem(row)
        }

        return ListTemplate.Builder()
            .setTitle("Özel Tuş Gönder")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
