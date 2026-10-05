package com.deku.autovnc.model

import java.io.Serializable
import java.util.UUID

data class VncServerProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var host: String = "",
    var port: Int = 5900,
    var password: String = "",
    var scaleMode: ScaleMode = ScaleMode.FIT,
    var isDefault: Boolean = false,
    var fps: Int = 30,
    var colorDepth: Int = 32
) : Serializable
