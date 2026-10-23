package com.swipes.capture

import android.content.Context
import java.security.SecureRandom

/** Shared secret the app hands to the helper at launch; persisted so reconnects keep working. */
object HelperToken {
    private const val PREFS = "helper"
    private const val KEY = "token"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY, token).apply()
        return token
    }
}
