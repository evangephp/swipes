package com.swipes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User settings. */
object Prefs {
    data class Settings(val statusNotification: Boolean = true, val milestoneNotifications: Boolean = true)

    private const val FILE = "settings"
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load(context: Context): Settings {
        if (!loaded) {
            val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            _settings.value = Settings(p.getBoolean("status_notification", true), p.getBoolean("milestone_notifications", true))
            loaded = true
        }
        return _settings.value
    }

    fun update(context: Context, f: (Settings) -> Settings) {
        val s = f(load(context))
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("status_notification", s.statusNotification)
            .putBoolean("milestone_notifications", s.milestoneNotifications)
            .apply()
        _settings.value = s
    }
}
