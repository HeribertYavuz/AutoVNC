package com.deku.autovnc.model

enum class ScaleMode(val title: String) {
    FIT("Ekrana Sığdır"),
    FILL("Ekranı Doldur"),
    ORIGINAL("Birebir (1:1)");

    fun next(): ScaleMode {
        val values = values()
        return values[(ordinal + 1) % values.size]
    }
}
