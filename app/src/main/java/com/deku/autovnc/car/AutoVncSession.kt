package com.deku.autovnc.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.deku.autovnc.vnc.VncState

class AutoVncSession : Session() {
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                // When car session disconnects, cleanly disconnect VNC
                VncState.disconnect(carContext)
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return CarConnectionScreen(carContext)
    }
}
