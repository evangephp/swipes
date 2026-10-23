package com.swipes.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import com.swipes.adb.AdbState
import com.swipes.capture.CaptureEngine

enum class Step { NOTIFICATIONS, ACCESSIBILITY, DEVELOPER_OPTIONS, USB_DEBUGGING, WIRELESS_DEBUGGING, PAIRED, HELPER, BATTERY }

/** null = can't tell (e.g. a setting the app isn't allowed to read). */
typealias Checks = Map<Step, Boolean?>

object SetupChecks {
    /** Steps recording depends on; battery is only recommended. */
    val required = Step.entries - Step.BATTERY

    fun read(context: Context, engine: CaptureEngine.State): Checks {
        val cr = context.contentResolver
        fun global(name: String): Boolean? =
            runCatching { Settings.Global.getInt(cr, name) == 1 }.getOrNull()
        val recording = engine.status == CaptureEngine.Status.RECORDING
        return mapOf(
            Step.NOTIFICATIONS to (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
            Step.ACCESSIBILITY to engine.accessibilityConnected,
            Step.DEVELOPER_OPTIONS to global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            Step.USB_DEBUGGING to global(Settings.Global.ADB_ENABLED),
            // Only needed to (re)start the reader; once it runs, Wireless debugging may be off.
            Step.WIRELESS_DEBUGGING to if (recording) true else global("adb_wifi_enabled"),
            Step.PAIRED to AdbState.isPaired(context),
            Step.HELPER to recording,
            Step.BATTERY to context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    fun complete(checks: Checks) = required.all { checks[it] == true }
}
