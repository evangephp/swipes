package com.swipes.milestones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.swipes.R
import com.swipes.data.MilestoneRuleEntity
import com.swipes.ui.MainActivity

object MilestoneNotifier {
    private const val CHANNEL = "milestones"

    fun init(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Milestones", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notify(context: Context, rule: MilestoneRuleEntity) {
        val metric = Metric.of(rule.metric)
        val period = Period.of(rule.period)
        val unit = DistanceUnit.of(rule.unit)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_touch)
            .setContentTitle("Milestone reached")
            .setContentText(Milestones.message(metric, period, rule.threshold, unit))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // callers check canNotify()
        NotificationManagerCompat.from(context).notify(1000 + rule.id.toInt(), notification)
    }
}
