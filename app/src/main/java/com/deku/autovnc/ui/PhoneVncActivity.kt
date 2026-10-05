package com.deku.autovnc.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deku.autovnc.car.ClickMode
import com.deku.autovnc.databinding.ActivityPhoneVncBinding
import com.deku.autovnc.model.VncServerProfile
import com.deku.autovnc.vnc.ConnectionStatus
import com.deku.autovnc.vnc.VncKeys
import com.deku.autovnc.vnc.VncState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PhoneVncActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneVncBinding
    private var profile: VncServerProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhoneVncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("server_profile", VncServerProfile::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("server_profile") as? VncServerProfile
        }

        if (profile == null) {
            Toast.makeText(this, "Geçersiz sunucu profili", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.vncView.scaleMode = profile!!.scaleMode

        setupListeners()
        observeVncState()

        // Connect
        VncState.connect(this, profile!!)
    }

    private fun setupListeners() {
        binding.btnDisconnect.setOnClickListener {
            VncState.disconnect(this)
            finish()
        }

        binding.btnMouseMode.setOnClickListener {
            val next = binding.vncView.clickMode.next()
            binding.vncView.clickMode = next
            binding.btnMouseMode.text = next.title
        }

        binding.btnCtrlAltDel.setOnClickListener {
            VncState.activeClient?.sendCtrlAltDel()
            Toast.makeText(this, "Ctrl+Alt+Del gönderildi", Toast.LENGTH_SHORT).show()
        }

        binding.btnRefresh.setOnClickListener {
            VncState.activeClient?.requestFullRefresh()
            Toast.makeText(this, "Yenileme istendi", Toast.LENGTH_SHORT).show()
        }

        binding.btnKeyboard.setOnClickListener {
            showTextInputDialog()
        }

        binding.btnCarControls.setOnClickListener {
            val isHidden = VncState.toggleCarControlsHidden(this)
            updateCarControlsButton(isHidden)
            val msg = if (isHidden) "Araç ekranı tuşları gizlendi (Tam Ekran)" else "Araç ekranı tuşları açıldı"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        updateCarControlsButton(VncState.hideCarControls.value)
    }

    private fun updateCarControlsButton(isHidden: Boolean) {
        if (isHidden) {
            binding.btnCarControls.text = "🚗 Gizli"
            binding.btnCarControls.setBackgroundResource(com.deku.autovnc.R.drawable.btn_secondary_white)
            binding.btnCarControls.setTextColor(androidx.core.content.ContextCompat.getColor(this, com.deku.autovnc.R.color.eel_black))
        } else {
            binding.btnCarControls.text = "🚗 Görünür"
            binding.btnCarControls.setBackgroundResource(com.deku.autovnc.R.drawable.btn_owl_green)
            binding.btnCarControls.setTextColor(androidx.core.content.ContextCompat.getColor(this, com.deku.autovnc.R.color.snow))
        }
    }

    private fun showTextInputDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Uzak masaüstüne yazılacak metin…"
        }

        AlertDialog.Builder(this)
            .setTitle("Klavye Girişi")
            .setView(input)
            .setPositiveButton("Gönder") { _, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    VncState.activeClient?.sendText(text)
                }
            }
            .setNeutralButton("Enter") { _, _ ->
                VncState.activeClient?.sendKeyPress(VncKeys.KEY_RETURN)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun observeVncState() {
        VncState.status
            .onEach { status ->
                when (status) {
                    ConnectionStatus.CONNECTING -> {
                        binding.tvStatus.text = "Bağlanıyor…"
                        binding.tvStatus.setTextColor(android.graphics.Color.YELLOW)
                    }
                    ConnectionStatus.CONNECTED -> {
                        binding.tvStatus.text = VncState.statusMessage.value
                        binding.tvStatus.setTextColor(android.graphics.Color.GREEN)
                    }
                    ConnectionStatus.DISCONNECTED -> {
                        binding.tvStatus.text = "Bağlantı Kesildi"
                        binding.tvStatus.setTextColor(android.graphics.Color.WHITE)
                    }
                    ConnectionStatus.ERROR -> {
                        binding.tvStatus.text = VncState.statusMessage.value
                        binding.tvStatus.setTextColor(android.graphics.Color.RED)
                        Toast.makeText(this, VncState.statusMessage.value, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .launchIn(lifecycleScope)

        VncState.frameUpdateTrigger
            .onEach {
                binding.vncView.postInvalidate()
            }
            .launchIn(lifecycleScope)

        VncState.hideCarControls
            .onEach { isHidden ->
                updateCarControlsButton(isHidden)
            }
            .launchIn(lifecycleScope)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val client = VncState.activeClient
        if (client != null && client.isConnected.get()) {
            val keySym = VncKeys.getKeysymFromAndroidKey(event.keyCode, event)
            if (keySym != 0) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    client.sendKeyEvent(keySym, true)
                    return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    client.sendKeyEvent(keySym, false)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only disconnect if not connected in car
        // If needed, leave alive or disconnect:
    }
}
