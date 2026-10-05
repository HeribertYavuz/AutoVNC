package com.deku.autovnc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.deku.autovnc.R
import com.deku.autovnc.data.ServerRepository
import com.deku.autovnc.databinding.ActivityMainBinding
import com.deku.autovnc.databinding.DialogServerBinding
import com.deku.autovnc.model.ScaleMode
import com.deku.autovnc.model.VncServerProfile
import com.deku.autovnc.vnc.VncState

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ServerRepository
    private lateinit var adapter: ServerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ServerRepository(this)
        VncState.initSettings(this)

        checkPermissions()
        setupRecyclerView()
        setupCarControlsToggle()

        binding.fabAdd.setOnClickListener {
            showServerDialog(null)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        updateCarControlsUi()
    }

    private fun setupCarControlsToggle() {
        updateCarControlsUi()
        binding.btnToggleCarControls.setOnClickListener {
            val isHidden = VncState.toggleCarControlsHidden(this)
            updateCarControlsUi()
            val msg = if (isHidden) {
                "Android Auto ekran tuşları gizlendi (Tam Ekran Modu)"
            } else {
                "Android Auto ekran tuşları görünür yapıldı"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCarControlsUi() {
        val isHidden = repository.isCarControlsHidden()
        if (isHidden) {
            binding.tvCarControlsBadge.text = "Gizli (Tam Ekran)"
            binding.tvCarControlsBadge.setBackgroundResource(R.drawable.chip_duo_gray)
            binding.tvCarControlsBadge.setTextColor(ContextCompat.getColor(this, R.color.wolf))
            binding.btnToggleCarControls.text = "🚗 Tuşlar Gizli (Göstermek İçin Dokun)"
            binding.btnToggleCarControls.setBackgroundResource(R.drawable.btn_secondary_white)
            binding.btnToggleCarControls.setTextColor(ContextCompat.getColor(this, R.color.eel_black))
        } else {
            binding.tvCarControlsBadge.text = "Görünür"
            binding.tvCarControlsBadge.setBackgroundResource(R.drawable.chip_duo_pill)
            binding.tvCarControlsBadge.setTextColor(ContextCompat.getColor(this, R.color.owl_green_deep))
            binding.btnToggleCarControls.text = "🚗 Tuşlar Açık (Gizlemek İçin Dokun)"
            binding.btnToggleCarControls.setBackgroundResource(R.drawable.btn_owl_green)
            binding.btnToggleCarControls.setTextColor(ContextCompat.getColor(this, R.color.snow))
        }
    }

    private fun setupRecyclerView() {
        adapter = ServerAdapter(
            servers = mutableListOf(),
            onConnectClick = { profile ->
                val intent = Intent(this, PhoneVncActivity::class.java).apply {
                    putExtra("server_profile", profile)
                }
                startActivity(intent)
            },
            onEditClick = { profile ->
                showServerDialog(profile)
            },
            onDeleteClick = { profile ->
                AlertDialog.Builder(this)
                    .setTitle("Sunucuyu Sil")
                    .setMessage("${profile.name} sunucusunu silmek istediğinizden emin misiniz?")
                    .setPositiveButton("Sil") { _, _ ->
                        repository.deleteServer(profile.id)
                        refreshList()
                        Toast.makeText(this, "Sunucu silindi", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("İptal", null)
                    .show()
            }
        )

        binding.rvServers.layoutManager = LinearLayoutManager(this)
        binding.rvServers.adapter = adapter
        refreshList()
    }

    private fun refreshList() {
        val servers = repository.getServers()
        adapter.updateList(servers)
        binding.tvEmpty.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
        binding.tvServerCount.text = "${servers.size} Sunucu"
    }

    private fun showServerDialog(existing: VncServerProfile?) {
        val dialogBinding = DialogServerBinding.inflate(LayoutInflater.from(this))

        // FPS Options
        val fpsOptions = listOf(
            Pair("15 FPS (Tasarruf - Düşük Ağ Kullanımı)", 15),
            Pair("30 FPS (Önerilen / Standart)", 30),
            Pair("45 FPS (Akıcı Deneyim)", 45),
            Pair("60 FPS (Ultra Akıcı - Yüksek Performans)", 60)
        )
        val fpsAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            fpsOptions.map { it.first }
        )
        dialogBinding.spFps.adapter = fpsAdapter

        // Bitrate / Color Depth Options
        val colorDepthOptions = listOf(
            Pair("32-bit ARGB8888 (Kayıpsız / Maksimum Kalite)", 32),
            Pair("16-bit RGB565 (Dengeli / %50 Daha Az Bitrate)", 16)
        )
        val depthAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            colorDepthOptions.map { it.first }
        )
        dialogBinding.spColorDepth.adapter = depthAdapter

        // Scale Modes
        val scaleModes = ScaleMode.values()
        val scaleAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            scaleModes.map { it.title }
        )
        dialogBinding.spScaleMode.adapter = scaleAdapter

        if (existing != null) {
            dialogBinding.tvDialogTitle.text = "Sunucuyu Düzenle"
            dialogBinding.etName.setText(existing.name)
            dialogBinding.etHost.setText(existing.host)
            dialogBinding.etPort.setText(existing.port.toString())
            dialogBinding.etPassword.setText(existing.password)
            dialogBinding.spScaleMode.setSelection(existing.scaleMode.ordinal)

            val fpsIdx = fpsOptions.indexOfFirst { it.second == existing.fps }
            if (fpsIdx >= 0) dialogBinding.spFps.setSelection(fpsIdx) else dialogBinding.spFps.setSelection(1) // 30 FPS

            val depthIdx = colorDepthOptions.indexOfFirst { it.second == existing.colorDepth }
            if (depthIdx >= 0) dialogBinding.spColorDepth.setSelection(depthIdx) else dialogBinding.spColorDepth.setSelection(0)
        } else {
            dialogBinding.tvDialogTitle.text = "Yeni VNC Sunucusu"
            dialogBinding.spFps.setSelection(1) // 30 FPS default
            dialogBinding.spColorDepth.setSelection(0) // 32-bit default
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etName.text.toString().trim().ifEmpty { "VNC Sunucusu" }
            val host = dialogBinding.etHost.text.toString().trim()
            val port = dialogBinding.etPort.text.toString().trim().toIntOrNull() ?: 5900
            val pass = dialogBinding.etPassword.text.toString()
            val scale = scaleModes[dialogBinding.spScaleMode.selectedItemPosition]
            val fps = fpsOptions[dialogBinding.spFps.selectedItemPosition].second
            val depth = colorDepthOptions[dialogBinding.spColorDepth.selectedItemPosition].second

            if (host.isEmpty()) {
                Toast.makeText(this, "Lütfen sunucu IP veya alan adı girin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val profile = existing ?: VncServerProfile(name = name, host = host)
            profile.name = name
            profile.host = host
            profile.port = port
            profile.password = pass
            profile.scaleMode = scale
            profile.fps = fps
            profile.colorDepth = depth

            repository.saveServer(profile)
            refreshList()
            dialog.dismiss()
            Toast.makeText(this, "$name ($fps FPS, $depth-bit) kaydedildi", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
