package com.swipes.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.swipes.Prefs
import com.swipes.R
import com.swipes.capture.CaptureEngine.Status
import com.swipes.ui.MainActivity

/** Ongoing notification showing whether capture is running. */
object StatusNotifier {
    // A channel's badge setting can't change after creation, so the badge-free channel has a new id.
    private const val CHANNEL = "status_quiet"
    private const val OLD_CHANNEL = "status"
    private const val ID = 1
    /** Count-only updates; status changes post immediately. */
    private const val MIN_INTERVAL_MS = 60_000L

    private lateinit var app: Context
    private var lastStatus: Status? = null
    private var lastPostMs = 0L

    fun init(context: Context) {
        app = context.applicationContext
        // No red dot on the app icon for a notification that is always there.
        val channel = NotificationChannel(CHANNEL, "Recording status", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        app.getSystemService(NotificationManager::class.java).apply {
            deleteNotificationChannel(OLD_CHANNEL)
            createNotificationChannel(channel)
        }
    }

    @Synchronized
    fun update(state: CaptureEngine.State, force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && state.status == lastStatus && now - lastPostMs < MIN_INTERVAL_MS) return
        val nm = NotificationManagerCompat.from(app)
        if (!Prefs.load(app).statusNotification) {
            nm.cancel(ID)
            lastStatus = null
            return
        }
        if (!nm.areNotificationsEnabled()) return
        lastStatus = state.status
        lastPostMs = now

        val today = state.today
        val title = when (state.status) {
            Status.RECORDING -> "Recording"
            Status.NEEDS_PAIRING -> "Paused: pairing needed"
            Status.NEEDS_WIRELESS_DEBUGGING -> "Paused: turn on Wireless debugging"
            else -> "Starting…"
        }
        val text = today?.let { "Today: %,d taps · %,d swipes · %,d typing".format(it.taps, it.swipes, it.typing) }
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_touch)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
        @Suppress("MissingPermission") // guarded by areNotificationsEnabled()
        nm.notify(ID, notification)
    }
}
