package com.deku.autovnc.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class AutoVncCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // Allows connection from Android Auto host (OEM head units, DHU, wireless adapters)
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return AutoVncSession()
    }
}
