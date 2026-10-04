package com.swipecounter.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.swipecounter.R
import com.swipecounter.capture.CaptureEngine
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Pairs with this device's Wireless debugging. The Settings pairing dialog closes if the user
 * leaves it, so the code is typed into a notification reply while the dialog stays open.
 */
object Pairing {
    private const val TAG = "Pairing"
    private const val CHANNEL = "setup"
    private const val NOTIFICATION_ID = 100
    private const val KEY_CODE = "code"

    sealed interface State {
        data object Idle : State
        data object Searching : State
        data object AwaitingCode : State
        data object Pairing : State
        data object Paired : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var mdns: AdbMdns? = null
    @Volatile private var target: Pair<InetAddress, Int>? = null

    fun start(context: Context) {
        val app = context.applicationContext
        stopDiscovery()
        ensureChannel(app)
        setState(app, State.Searching)
        mdns = AdbMdns(app, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { host, port ->
            if (host != null && port > 0) {
                target = host to port
                val current = _state.value
                if (current == State.Searching || current is State.Failed) setState(app, State.AwaitingCode)
            } else {
                target = null
            }
        }.also { it.start() }
    }

    fun cancel(context: Context) {
        stopDiscovery()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        setState(context, State.Idle)
    }

    /** Submits a code typed in the notification reply or in the app (split-screen with Settings). */
    fun onCode(context: Context, code: String) {
        val (host, port) = target ?: return setState(context, State.Failed("Pairing dialog closed, open it again"))
        setState(context, State.Pairing)
        thread(name = "adb-pair") {
            try {
                AdbManager.get(context).pair(host.hostAddress, port, code.trim())
                AdbState.setPaired(context, true)
                stopDiscovery()
                setState(context, State.Paired)
                CaptureEngine.launchHelper("just paired")
            } catch (e: Exception) {
                Log.w(TAG, "pairing failed", e)
                setState(context, State.Failed("Wrong code or the dialog closed. Try again."))
            }
        }
    }

    private fun stopDiscovery() {
        mdns?.stop()
        mdns = null
        target = null
    }

    private fun setState(context: Context, newState: State) {
        _state.value = newState
        notify(context, newState)
    }

    private fun notify(context: Context, state: State) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_touch)
            .setContentTitle("Swipe Counter pairing")
            // Re-alert when the code is needed so it pops up as heads-up over the Settings dialog.
            .setOnlyAlertOnce(state != State.AwaitingCode && state !is State.Failed)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        when (state) {
            State.Idle -> return nm.cancel(NOTIFICATION_ID)
            State.Searching -> builder.setContentText("Open Wireless debugging → Pair device with pairing code").setOngoing(true)
            State.AwaitingCode, is State.Failed -> {
                val text = if (state is State.Failed) state.message else "Enter the 6-digit code shown in Settings"
                builder.setContentText(text).setOngoing(true).addAction(codeAction(context))
            }
            State.Pairing -> builder.setContentText("Pairing…").setOngoing(true)
            State.Paired -> builder.setContentText("Paired. You can close Settings.").setTimeoutAfter(10_000)
        }
        @Suppress("MissingPermission") // guarded by areNotificationsEnabled()
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun codeAction(context: Context): NotificationCompat.Action {
        val intent = Intent(context, CodeReceiver::class.java)
        // RemoteInput fills in the reply, so this PendingIntent must be mutable.
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val input = RemoteInput.Builder(KEY_CODE).setLabel("Pairing code").build()
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_touch, "Enter code", pending)
            .addRemoteInput(input)
            .build()
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Setup", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    class CodeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString() ?: return
            onCode(context.applicationContext, code)
        }
    }
}
