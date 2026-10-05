package com.deku.autovnc.data

import android.content.Context
import android.content.SharedPreferences
import com.deku.autovnc.model.ScaleMode
import com.deku.autovnc.model.VncServerProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ServerRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("autovnc_servers", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val keyServers = "saved_servers"
    private val keyHideCarControls = "hide_car_controls"
    private val keyDefaultFps = "default_fps"
    private val keyDefaultColorDepth = "default_color_depth"

    fun getServers(): MutableList<VncServerProfile> {
        val json = prefs.getString(keyServers, null) ?: return initializeDefaultServers()
        val type = object : TypeToken<MutableList<VncServerProfile>>() {}.type
        val list: MutableList<VncServerProfile>? = gson.fromJson(json, type)
        val result = list ?: initializeDefaultServers()
        // Ensure defaults for older persisted items
        for (item in result) {
            if (item.fps <= 0) item.fps = 30
            if (item.colorDepth <= 0) item.colorDepth = 32
        }
        return result
    }

    fun saveServer(profile: VncServerProfile) {
        val list = getServers()
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            list[index] = profile
        } else {
            if (list.isEmpty()) {
                profile.isDefault = true
            }
            list.add(profile)
        }
        persist(list)
    }

    fun deleteServer(id: String) {
        val list = getServers()
        list.removeAll { it.id == id }
        if (list.isNotEmpty() && list.none { it.isDefault }) {
            list[0].isDefault = true
        }
        persist(list)
    }

    fun setDefaultServer(id: String) {
        val list = getServers()
        for (item in list) {
            item.isDefault = (item.id == id)
        }
        persist(list)
    }

    fun getDefaultServer(): VncServerProfile? {
        val list = getServers()
        return list.firstOrNull { it.isDefault } ?: list.firstOrNull()
    }

    fun isCarControlsHidden(): Boolean {
        return prefs.getBoolean(keyHideCarControls, false)
    }

    fun setCarControlsHidden(hidden: Boolean) {
        prefs.edit().putBoolean(keyHideCarControls, hidden).apply()
    }

    fun getDefaultFps(): Int {
        return prefs.getInt(keyDefaultFps, 30)
    }

    fun setDefaultFps(fps: Int) {
        prefs.edit().putInt(keyDefaultFps, fps).apply()
    }

    fun getDefaultColorDepth(): Int {
        return prefs.getInt(keyDefaultColorDepth, 32)
    }

    fun setDefaultColorDepth(depth: Int) {
        prefs.edit().putInt(keyDefaultColorDepth, depth).apply()
    }

    private fun persist(list: List<VncServerProfile>) {
        val json = gson.toJson(list)
        prefs.edit().putString(keyServers, json).apply()
    }

    private fun initializeDefaultServers(): MutableList<VncServerProfile> {
        val defaultList = mutableListOf(
            VncServerProfile(
                name = "Yerel Bilgisayar",
                host = "192.168.1.100",
                port = 5900,
                password = "",
                scaleMode = ScaleMode.FIT,
                isDefault = true,
                fps = 30,
                colorDepth = 32
            )
        )
        persist(defaultList)
        return defaultList
    }
}
