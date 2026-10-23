package com.swipes.adb

import android.content.Context

/** Persisted adb setup flags. */
object AdbState {
    private const val PREFS = "adb"

    fun isPaired(context: Context) = prefs(context).getBoolean("paired", false)

    fun setPaired(context: Context, paired: Boolean) = prefs(context).edit().putBoolean("paired", paired).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
