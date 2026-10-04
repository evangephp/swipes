package com.swipecounter.adb

import android.content.Context
import android.util.Log
import com.swipecounter.capture.HelperToken
import com.swipecounter.helper.HelperMain
import io.github.muntashirakon.adb.AdbPairingRequiredException

/** Starts (or restarts) the shell-uid helper over the device's own Wireless debugging adbd. */
object HelperLauncher {
    private const val TAG = "HelperLauncher"
    const val PROCESS_NAME = "swipecounter-helper"

    sealed interface Result {
        data object Launched : Result
        data object NeedsPairing : Result
        data class Failed(val message: String) : Result
    }

    /** Blocking; call off the main thread. */
    @Synchronized
    fun launch(context: Context): Result {
        val adb = AdbManager.get(context)
        return try {
            if (!adb.isConnected && !adb.connectTls(context, 10_000)) {
                return Result.Failed("Wireless debugging not found")
            }
            AdbState.setPaired(context, true)
            // An interactive shell keeps the token out of adbd's logged command line and argv.
            adb.openStream("shell:").use { stream ->
                stream.openOutputStream().apply {
                    write(script(context).toByteArray())
                    flush()
                }
                val output = stream.openInputStream().bufferedReader().readText()
                Log.d(TAG, "shell exited: ${output.takeLast(300).replace(HelperToken.get(context), "<token>")}")
            }
            Result.Launched
        } catch (e: AdbPairingRequiredException) {
            AdbState.setPaired(context, false)
            Result.NeedsPairing
        } catch (e: Exception) {
            Log.w(TAG, "launch failed", e)
            Result.Failed(e.message ?: e.toString())
        } finally {
            runCatching { adb.disconnect() }
        }
    }

    private fun script(context: Context): String {
        val apk = context.applicationInfo.sourceDir
        val token = HelperToken.get(context)
        // pidof matches the process name only; pkill -f would also match this shell's own command line.
        return """
            kill ${'$'}(pidof $PROCESS_NAME) 2>/dev/null
            echo $token | CLASSPATH=$apk setsid app_process /system/bin --nice-name=$PROCESS_NAME ${HelperMain::class.java.name} >/dev/null 2>&1 &
            exit
        """.trimIndent() + "\n"
    }
}
