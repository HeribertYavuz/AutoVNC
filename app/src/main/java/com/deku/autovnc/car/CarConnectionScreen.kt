package com.deku.autovnc.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.deku.autovnc.data.ServerRepository
import com.deku.autovnc.model.VncServerProfile
import com.deku.autovnc.vnc.ConnectionStatus
import com.deku.autovnc.vnc.VncState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class CarConnectionScreen(carContext: CarContext) : Screen(carContext) {
    private val repository = ServerRepository(carContext)
    private var servers: List<VncServerProfile> = emptyList()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                servers = repository.getServers()
                invalidate()

                VncState.status
                    .onEach { status ->
                        if (status == ConnectionStatus.CONNECTED && screenManager.top !is CarVncScreen) {
                            val active = VncState.activeProfile
                            if (active != null) {
                                try {
                                    screenManager.push(CarVncScreen(carContext, active))
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    .launchIn(lifecycleScope)
            }
        })
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (servers.isEmpty()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Kayıtlı VNC Sunucusu Yok")
                    .addText("Lütfen telefonunuzdaki AutoVNC uygulamasından yeni sunucu ekleyin.")
                    .build()
            )
        } else {
            for (profile in servers) {
                val isCurrentActive = VncState.activeProfile?.id == profile.id &&
                        VncState.status.value == ConnectionStatus.CONNECTED

                val statusText = if (isCurrentActive) {
                    "● Bağlı - Oturuma Dönmek İçin Dokunun"
                } else {
                    "${profile.host}:${profile.port}"
                }

                val row = Row.Builder()
                    .setTitle(profile.name)
                    .addText(statusText)
                    .setOnClickListener {
                        if (isCurrentActive) {
                            screenManager.push(CarVncScreen(carContext, profile))
                        } else {
                            CarToast.makeText(
                                carContext,
                                "${profile.name} bağlanıyor...",
                                CarToast.LENGTH_SHORT
                            ).show()
                            VncState.connect(carContext, profile)
                            screenManager.push(CarVncScreen(carContext, profile))
                        }
                    }
                    .build()
                listBuilder.addItem(row)
            }
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Yenile")
                    .setOnClickListener {
                        servers = repository.getServers()
                        invalidate()
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("AutoVNC Sunucuları")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .setSingleList(listBuilder.build())
            .build()
    }
}
