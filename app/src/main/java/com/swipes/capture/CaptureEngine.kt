package com.swipes.capture

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import androidx.core.content.ContextCompat
import com.swipes.adb.AdbState
import com.swipes.adb.HelperLauncher
import com.swipes.data.AppDatabase
import com.swipes.data.CaptureGapEntity
import androidx.sqlite.db.SimpleSQLiteQuery
import com.swipes.data.DailyStatsEntity
import com.swipes.data.HourStatsEntity
import com.swipes.data.MetaEntity
import com.swipes.data.MilestoneFiredEntity
import com.swipes.data.MilestoneRuleEntity
import com.swipes.data.PointCodec
import com.swipes.data.SessionEntity
import com.swipes.data.StrokeEntity
import com.swipes.helper.HelperInfo
import com.swipes.helper.Protocol
import com.swipes.milestones.DistanceUnit
import com.swipes.milestones.Metric
import com.swipes.milestones.MilestoneEvaluator
import com.swipes.milestones.MilestoneNotifier
import com.swipes.milestones.Milestones
import com.swipes.milestones.Period
import com.swipes.milestones.PeriodTotals
import com.swipes.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Process-wide capture pipeline: helper samples → strokes → privacy mask → in-memory buffer →
 * periodic flush to Room. All pipeline state is confined to the single [worker] thread.
 */
object CaptureEngine {
    private const val TAG = "CaptureEngine"
    // Lets keyboard/lock events that arrive slightly after a stroke still mask it.
    private const val FINALIZE_DELAY_MS = 500L
    private const val FLUSH_EVERY_STROKES = 200
    private const val FLUSH_EVERY_MS = 30_000L
    private const val AUTO_LAUNCH_INTERVAL_MS = 60_000L
    private const val RECENT = 20

    private const val META_INSTANCE = "resume_instance"
    private const val META_SEQ = "resume_seq"
    private const val META_LAST_WALL = "last_sample_wall_ms"
    private const val META_MILESTONES_SEEDED = "milestones_seeded"
    private const val META_STATS_V3 = "stats_v3"
    private const val MILESTONE_EVAL_MS = 1_000L
    private const val MILESTONE_NOTIFY_INTERVAL_MS = 30_000L

    enum class Status { STARTING, CONNECTING, LAUNCHING, RECORDING, NEEDS_PAIRING, NEEDS_WIRELESS_DEBUGGING }

    /** A recent stroke with positions (masked strokes are never kept here). */
    class Recent(val stroke: Stroke, val kind: Kind, val pkg: String?)

    data class State(
        val status: Status = Status.STARTING,
        val detail: String = "",
        val info: HelperInfo? = null,
        val accessibilityConnected: Boolean = false,
        val locked: Boolean? = null,
        val today: DailyStatsEntity? = null,
        val samples: Long = 0,
        val recent: List<Recent> = emptyList(),
        /** Running totals for the current day/week/month/year, for milestone progress. */
        val periodTotals: Map<Period, DailyStatsEntity> = emptyMap(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val screen = ScreenContext()

    /** Mapping for the current session, for UI that converts panel units to the screen. */
    @Volatile var geometry: PanelGeometry? = null
        private set

    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "capture") }
    private lateinit var app: Context
    private lateinit var db: AppDatabase
    private lateinit var recorder: Recorder
    private var client: HelperClient? = null
    private var started = false

    // Worker-confined.
    private class Queued(val stroke: Stroke, val instance: Long, val sessionId: Long, val geometry: PanelGeometry, val readyAtMs: Long)
    private val finalizeQueue = ArrayDeque<Queued>()
    private var instance = 0L
    private var sessionId = 0L
    private var expectedSeq = -1L
    private var lastWallMs = 0L
    private var lastFlushMs = 0L
    private var dbToday = DailyStatsEntity("")
    private val recentStartWallMs = ArrayDeque<Long>()
    private var milestoneRules: List<MilestoneRuleEntity> = emptyList()
    private val milestonesFired = HashSet<Pair<Long, String>>()
    private var periodTotals = PeriodTotals("", emptyMap())
    private var milestonesDirty = true
    private var lastMilestoneEvalMs = 0L
    private var lastMilestoneNotifyMs = -MILESTONE_NOTIFY_INTERVAL_MS
    private val assembler = StrokeAssembler { s ->
        finalizeQueue += Queued(s, instance, sessionId, geometry!!, SystemClock.uptimeMillis() + FINALIZE_DELAY_MS)
    }

    private lateinit var keyguard: KeyguardManager
    private lateinit var power: PowerManager
    @Volatile private var lockedNow = true

    /** A helper is connected and streaming; launch outcomes must not override "recording" then. */
    @Volatile private var connected = false
    @Volatile private var lastLaunchAttemptMs = -AUTO_LAUNCH_INTERVAL_MS
    @Volatile private var launching = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        db = AppDatabase.get(app)
        recorder = Recorder(db)
        StatusNotifier.init(app)
        MilestoneNotifier.init(app)
        watchScreenState()
        watchDisplay()
        val c = HelperClient(HelperToken.get(app), Listener)
        client = c
        worker.execute {
            val dao = db.captureDao()
            val resumeInstance = dao.meta(META_INSTANCE)?.toLongOrNull() ?: 0L
            val resumeSeq = dao.meta(META_SEQ)?.toLongOrNull() ?: 0L
            instance = resumeInstance
            lastWallMs = dao.meta(META_LAST_WALL)?.toLongOrNull() ?: 0L
            recomputeStatsIfNeeded()
            loadMilestones()
            refreshToday()
            c.resumeFrom(resumeInstance, resumeSeq)
            c.start()
        }
        worker.scheduleWithFixedDelay(::tick, 250, 250, TimeUnit.MILLISECONDS)
    }

    fun setAccessibilityConnected(connected: Boolean) = publish { it.copy(accessibilityConnected = connected) }

    /** Flushes pending strokes and waits for it, so queries see everything finalized so far. */
    fun flushNow() {
        if (started) worker.submit(::flush).get()
    }

    /** Flush soon, e.g. on memory pressure or screen off. */
    fun requestFlush() {
        if (started) worker.execute(::flush)
    }

    /** Reload milestone rules after the user edits them. */
    fun milestonesChanged() {
        if (started) worker.execute(::loadMilestones)
    }

    /** Finalized strokes that started in [fromWallMs, toWallMs], from roughly the last 2000. */
    fun strokesStartedBetween(fromWallMs: Long, toWallMs: Long): Int {
        val task = worker.submit<Int> { recentStartWallMs.count { it in fromWallMs..toWallMs } }
        return task.get()
    }

    /** Explicit (re)launch, bypassing the auto-launch rate limit. */
    fun launchHelper(reason: String) {
        lastLaunchAttemptMs = -AUTO_LAUNCH_INTERVAL_MS
        maybeLaunch(reason)
    }

    private object Listener : HelperClient.Listener {
        override fun onConnected(info: HelperInfo) = worker.execute { handleConnected(info) }

        override fun onSamples(firstSeq: Long, count: Int, samples: LongArray) {
            val copy = samples.copyOf(count * Protocol.STRIDE)
            worker.execute { handleSamples(firstSeq, count, copy) }
        }

        override fun onIncompatibleHelper() = launchHelper("helper speaks an older protocol")

        override fun onDisconnected(reason: String, helperMissing: Boolean) {
            connected = false
            if (helperMissing) maybeLaunch(reason) else publish { it.copy(status = Status.CONNECTING, detail = reason) }
        }
    }

    private fun handleConnected(info: HelperInfo) {
        connected = true
        if (info.apkPath != app.applicationInfo.sourceDir) launchHelper("helper is from an older install")
        if (info.instanceId != instance || sessionId == 0L) {
            if (instance != 0L && info.instanceId != instance) {
                assembler.flushOpen()
                recordGap("helper restarted")
            }
            instance = info.instanceId
            expectedSeq = -1
            geometry = currentGeometry(info.xMax, info.yMax)
            sessionId = newSession(info)
        }
        publish { it.copy(status = Status.RECORDING, detail = "", info = info) }
    }

    private fun handleSamples(firstSeq: Long, count: Int, data: LongArray) {
        if (expectedSeq >= 0 && firstSeq > expectedSeq) {
            assembler.flushOpen()
            recordGap("sample buffer overrun")
        }
        for (i in 0 until count) {
            val seq = firstSeq + i
            if (seq < expectedSeq) continue
            val o = i * Protocol.STRIDE
            assembler.add(
                seq, data[o + Protocol.F_MONO], data[o + Protocol.F_WALL],
                data[o + Protocol.F_SLOT].toInt(), data[o + Protocol.F_TRACKING_ID].toInt(),
                data[o + Protocol.F_X].toInt(), data[o + Protocol.F_Y].toInt(), data[o + Protocol.F_MAJOR].toInt(),
            )
        }
        if (count > 0) lastWallMs = data[(count - 1) * Protocol.STRIDE + Protocol.F_WALL] / 1000
        expectedSeq = maxOf(expectedSeq, firstSeq + count)
        publish { it.copy(samples = it.samples + count) }
    }

    private fun tick() {
        try {
            val now = SystemClock.uptimeMillis()
            // USER_PRESENT doesn't fire when the screen comes back on within the lock grace period,
            // so poll the keyguard while we believe it's up.
            if (lockedNow && power.isInteractive && !keyguard.isKeyguardLocked) setLocked(now, false, "keyguard gone")
            while (finalizeQueue.isNotEmpty() && finalizeQueue.first().readyAtMs <= now) finalize(finalizeQueue.removeFirst())
            if (dbToday.day != Aggregates.dayKey(System.currentTimeMillis(), ZoneId.systemDefault())) {
                refreshToday()
                Widgets.requestUpdate(app, force = true)
            }
            if (recorder.size >= FLUSH_EVERY_STROKES || (recorder.size > 0 && now - lastFlushMs >= FLUSH_EVERY_MS)) flush()
            if (milestonesDirty && now - lastMilestoneEvalMs >= MILESTONE_EVAL_MS) evaluateMilestones(now)
        } catch (e: Exception) {
            Log.e(TAG, "tick failed", e)
        }
    }

    private fun finalize(q: Queued) {
        val s = q.stroke
        val startMs = s.startMonoUs / 1000
        val mask = PrivacyMask.decide(s, q.geometry, screen)
        val rotation = screen.rotation.at(startMs)
        val pkg = Attribution.packageAt(
            screen.windows.at(startMs),
            q.geometry.screenX(s.x(0), s.y(0), rotation),
            q.geometry.screenY(s.x(0), s.y(0), rotation),
        ) ?: screen.foregroundPackage.at(startMs)
        val summary = Classifier.summarize(s, q.geometry, rotation)
        val masked = mask != Mask.NONE
        val last = s.pointCount - 1
        val entity = StrokeEntity(
            sessionId = q.sessionId,
            helperInstance = q.instance,
            firstSeq = s.firstSeq,
            startWallMs = s.startWallUs / 1000,
            startMonoUs = s.startMonoUs,
            durationMs = s.durationMs,
            slot = s.slot,
            groupId = s.groupId,
            multiFinger = s.multiFinger,
            kind = summary.kind.code,
            scroll = summary.scroll,
            mask = mask.code,
            pkg = pkg,
            rotation = rotation,
            pathMm = summary.pathMm,
            pointCount = s.pointCount,
            x0 = if (masked) null else s.x(0),
            y0 = if (masked) null else s.y(0),
            x1 = if (masked) null else s.x(last),
            y1 = if (masked) null else s.y(last),
            points = if (masked) null else PointCodec.encode(s.points),
        )
        recorder.add(entity, RecordedStroke(s, summary, mask, q.geometry))
        milestonesDirty = true
        recentStartWallMs.addLast(entity.startWallMs)
        if (recentStartWallMs.size > 2000) recentStartWallMs.removeFirst()
        val zone = ZoneId.systemDefault()
        val day = Aggregates.dayKey(System.currentTimeMillis(), zone)
        if (dbToday.day != day) refreshToday()
        publish {
            it.copy(
                today = dbToday + recorder.pendingDaily(day, zone),
                recent = if (masked) it.recent else (listOf(Recent(s, summary.kind, pkg)) + it.recent).take(RECENT),
            )
        }
    }

    private fun flush() {
        val current = finalizeQueue.filter { it.instance == instance }.minOfOrNull { it.stroke.firstSeq }
        val resumeSeq = listOfNotNull(assembler.oldestOpenSeq(), current).minOrNull() ?: expectedSeq.coerceAtLeast(0)
        val meta = mapOf(META_INSTANCE to instance.toString(), META_SEQ to resumeSeq.toString(), META_LAST_WALL to lastWallMs.toString())
        val inserted = recorder.flush(ZoneId.systemDefault(), meta)
        lastFlushMs = SystemClock.uptimeMillis()
        refreshToday()
        Widgets.requestUpdate(app)
        Log.d(TAG, "flushed $inserted strokes, resume at $resumeSeq")
    }

    private fun refreshToday() {
        val zone = ZoneId.systemDefault()
        val day = Aggregates.dayKey(System.currentTimeMillis(), zone)
        dbToday = db.captureDao().dailyStats(day) ?: DailyStatsEntity(day)
        publish { it.copy(today = dbToday + recorder.pendingDaily(day, zone)) }
        val today = LocalDate.parse(day)
        periodTotals = PeriodTotals(
            day,
            Period.entries.associateWith { period ->
                val from = Milestones.periodStart(period, today).toString()
                db.captureDao().dailyStatsBetween(from, day).fold(DailyStatsEntity(day)) { acc, d -> acc + d.copy(day = day) }
            },
        )
        milestonesDirty = true
    }

    /**
     * Schema v3 split keyboard touches into `typing` and added hourly stats; rebuild both from the
     * stroke table once so history uses the same definitions as new data.
     */
    private fun recomputeStatsIfNeeded() {
        val dao = db.captureDao()
        if (dao.meta(META_STATS_V3) != null) return
        val zone = ZoneId.systemDefault()
        val daily = HashMap<String, DailyStatsEntity>()
        val hourly = HashMap<Long, HourStatsEntity>()
        val query = "SELECT startWallMs, kind, mask, scroll, multiFinger AND firstSeq = groupId, pathMm, durationMs FROM stroke"
        db.query(SimpleSQLiteQuery(query)).use { c ->
            while (c.moveToNext()) {
                val wallMs = c.getLong(0)
                val d = Aggregates.delta(
                    Aggregates.dayKey(wallMs, zone), c.getInt(1), c.getInt(2), c.getInt(3) != 0, c.getInt(4) != 0, c.getDouble(5), c.getLong(6),
                )
                daily[d.day] = (daily[d.day] ?: DailyStatsEntity(d.day)) + d
                val hour = Aggregates.hourStart(wallMs)
                hourly[hour] = (hourly[hour] ?: HourStatsEntity(hour)) + d
            }
        }
        db.runInTransaction {
            dao.clearDailyStats()
            dao.upsertDailyStats(daily.values.toList())
            dao.clearHourStats()
            dao.upsertHourStats(hourly.values.toList())
            dao.upsertMeta(MetaEntity(META_STATS_V3, "1"))
        }
        Log.i(TAG, "recomputed stats: ${daily.size} days, ${hourly.size} hours")
    }

    private fun loadMilestones() {
        val dao = db.milestoneDao()
        if (db.captureDao().meta(META_MILESTONES_SEEDED) == null) {
            val unit = DistanceUnit.default()
            listOf(
                MilestoneRuleEntity(metric = Metric.SCROLLS.code, period = Period.DAY.code, threshold = 1000.0, unit = unit.code, enabled = false),
                MilestoneRuleEntity(
                    metric = Metric.DISTANCE.code, period = Period.MONTH.code,
                    threshold = if (unit == DistanceUnit.MILES) 3.0 else 5.0, unit = unit.code, enabled = false,
                ),
                MilestoneRuleEntity(metric = Metric.TOUCHES.code, period = Period.WEEK.code, threshold = 10_000.0, unit = unit.code, enabled = false),
                MilestoneRuleEntity(metric = Metric.TOUCH_TIME.code, period = Period.DAY.code, threshold = 60.0, unit = unit.code, enabled = false),
            ).forEach(dao::insertRule)
            db.captureDao().upsertMeta(MetaEntity(META_MILESTONES_SEEDED, "1"))
        }
        milestoneRules = dao.rules()
        milestonesFired.clear()
        for (rule in milestoneRules) dao.firedKeys(rule.id).forEach { milestonesFired += rule.id to it }
        milestonesDirty = true
        Widgets.requestUpdate(app, force = true)
    }

    private fun evaluateMilestones(now: Long) {
        milestonesDirty = false
        lastMilestoneEvalMs = now
        val zone = ZoneId.systemDefault()
        val day = Aggregates.dayKey(System.currentTimeMillis(), zone)
        val pending = recorder.pendingDaily(day, zone)
        if (periodTotals.day != day) refreshToday()
        val totals = periodTotals.withPending(day, pending) ?: return
        publish { it.copy(periodTotals = totals) }
        if (!MilestoneNotifier.canNotify(app)) return
        for (reached in MilestoneEvaluator.due(milestoneRules, totals, milestonesFired, LocalDate.parse(day))) {
            // Rate-limited; anything skipped here fires on a later evaluation.
            if (now - lastMilestoneNotifyMs < MILESTONE_NOTIFY_INTERVAL_MS) {
                milestonesDirty = true
                break
            }
            milestonesFired += reached.rule.id to reached.periodKey
            val inserted = db.milestoneDao().insertFired(MilestoneFiredEntity(reached.rule.id, reached.periodKey, System.currentTimeMillis()))
            if (inserted != -1L) {
                MilestoneNotifier.notify(app, reached.rule)
                lastMilestoneNotifyMs = now
            }
        }
    }

    private fun recordGap(reason: String) {
        val now = System.currentTimeMillis()
        val start = if (lastWallMs > 0) lastWallMs else now
        db.captureDao().insertGap(CaptureGapEntity(startWallMs = start, endWallMs = now, reason = reason))
        Log.i(TAG, "capture gap: $reason")
    }

    private fun newSession(info: HelperInfo): Long {
        val g = geometry!!
        return db.captureDao().insertSession(
            SessionEntity(
                helperInstance = info.instanceId, startWallMs = System.currentTimeMillis(), deviceName = info.deviceName,
                panelMaxX = g.panelMaxX, panelMaxY = g.panelMaxY,
                naturalWidth = g.naturalWidth, naturalHeight = g.naturalHeight, dpi = g.dpi,
            ),
        )
    }

    @Suppress("DEPRECATION") // getRealMetrics is the simplest source of physical size + dpi from a non-UI context.
    private fun currentGeometry(panelMaxX: Int, panelMaxY: Int): PanelGeometry {
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics().also(display::getRealMetrics)
        val swapped = display.rotation % 2 == 1
        return PanelGeometry(
            panelMaxX, panelMaxY,
            if (swapped) m.heightPixels else m.widthPixels,
            if (swapped) m.widthPixels else m.heightPixels,
            (m.xdpi + m.ydpi) / 2,
        )
    }

    private fun maybeLaunch(reason: String) {
        val now = SystemClock.uptimeMillis()
        if (launching || now - lastLaunchAttemptMs < AUTO_LAUNCH_INTERVAL_MS) return
        if (!AdbState.isPaired(app)) {
            if (!connected) publish { it.copy(status = Status.NEEDS_PAIRING, detail = reason) }
            return
        }
        lastLaunchAttemptMs = now
        launching = true
        if (!connected) publish { it.copy(status = Status.LAUNCHING, detail = reason) }
        thread(name = "helper-launch") {
            val result = HelperLauncher.launch(app)
            launching = false
            Log.i(TAG, "launch ($reason): $result")
            publish {
                // The running helper keeps recording; a failed restart only matters once it's gone.
                if (connected) return@publish it
                when (result) {
                    HelperLauncher.Result.Launched -> it.copy(status = Status.CONNECTING, detail = "helper launched")
                    HelperLauncher.Result.NeedsPairing -> it.copy(status = Status.NEEDS_PAIRING, detail = "pairing was removed")
                    is HelperLauncher.Result.Failed -> it.copy(status = Status.NEEDS_WIRELESS_DEBUGGING, detail = result.message)
                }
            }
        }
    }

    private fun watchScreenState() {
        keyguard = app.getSystemService(KeyguardManager::class.java)
        power = app.getSystemService(PowerManager::class.java)
        setLocked(SystemClock.uptimeMillis(), keyguard.isKeyguardLocked, "start")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val now = SystemClock.uptimeMillis()
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        setLocked(now, true, "screen off")
                        requestFlush()
                    }
                    Intent.ACTION_SCREEN_ON -> setLocked(now, keyguard.isKeyguardLocked, "screen on")
                    Intent.ACTION_USER_PRESENT -> setLocked(now, false, "user present")
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun setLocked(timeMs: Long, locked: Boolean, why: String) {
        lockedNow = locked
        screen.locked.set(timeMs, locked)
        Log.i(TAG, "locked=$locked ($why)")
        publish { it.copy(locked = locked) }
    }

    private fun watchDisplay() {
        val dm = app.getSystemService(DisplayManager::class.java)
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        screen.rotation.set(SystemClock.uptimeMillis(), display.rotation)
        dm.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                screen.rotation.set(SystemClock.uptimeMillis(), display.rotation)
                worker.execute {
                    val g = geometry ?: return@execute
                    val updated = currentGeometry(g.panelMaxX, g.panelMaxY)
                    // Resolution mode changes (e.g. FHD+ ↔ WQHD+) need a new session for correct mapping.
                    if (updated != g) {
                        geometry = updated
                        _state.value.info?.let { sessionId = newSession(it) }
                    }
                }
            }

            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit
        }, Handler(Looper.getMainLooper()))
    }

    private fun publish(f: (State) -> State) {
        val before = _state.value
        _state.update(f)
        val after = _state.value
        if (before.status != after.status || before.today != after.today) StatusNotifier.update(after)
    }
}
