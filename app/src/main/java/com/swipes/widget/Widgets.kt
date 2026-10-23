package com.swipes.widget

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.swipes.ui.AppColors
import com.swipes.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Three small widgets meant to be stacked with One UI's widget stack and swiped between. */
object Widgets {
    private const val MIN_INTERVAL_MS = 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var lastUpdateMs = -MIN_INTERVAL_MS

    /** Refreshes all widgets, at most once a minute unless [force]. */
    fun requestUpdate(context: Context, force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastUpdateMs < MIN_INTERVAL_MS) return
        lastUpdateMs = now
        val app = context.applicationContext
        scope.launch {
            runCatching {
                TodayWidget().updateAll(app)
                TopAppsWidget().updateAll(app)
                MilestoneWidget().updateAll(app)
            }
        }
    }
}

private suspend fun load(context: Context) = withContext(Dispatchers.IO) { WidgetData.load(context) }

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(title, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(6.dp))
        content()
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("%,d".format(value), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold))
        Text("  $label", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
    }
}

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent {
            GlanceTheme {
                Card("Today") {
                    Stat(data.today.taps, "taps")
                    Stat(data.today.swipes, "swipes")
                    Stat(data.today.typing, "typing")
                }
            }
        }
    }
}

class TopAppsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent {
            GlanceTheme {
                Card("Most touched today") {
                    if (data.topApps.isEmpty()) {
                        Text("No touches yet", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                    }
                    val max = data.topApps.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                    data.topApps.forEach { app ->
                        Row(GlanceModifier.fillMaxWidth()) {
                            Text(app.name, GlanceModifier.defaultWeight(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
                            Text("%,d".format(app.count), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                        }
                        val color = AppColors.color(app.pkg, dark = false) to AppColors.color(app.pkg, dark = true)
                        LinearProgressIndicator(
                            progress = app.count.toFloat() / max,
                            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                            color = ColorProvider(day = color.first, night = color.second),
                            backgroundColor = ColorProvider(day = Color(0x14000000), night = Color(0x22FFFFFF)),
                        )
                        Spacer(GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

class MilestoneWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent {
            GlanceTheme {
                Card("Next milestone") {
                    val m = data.milestone
                    if (m == null) {
                        Text("Turn on a milestone in the app.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
                    } else {
                        Text(m.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 2)
                        Spacer(GlanceModifier.height(8.dp))
                        LinearProgressIndicator(progress = m.progress, modifier = GlanceModifier.fillMaxWidth().height(8.dp))
                        Spacer(GlanceModifier.height(6.dp))
                        Text(m.caption, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
                    }
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class TopAppsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopAppsWidget()
}

class MilestoneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MilestoneWidget()
}
