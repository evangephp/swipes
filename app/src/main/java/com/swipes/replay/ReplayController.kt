package com.swipes.replay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swipes.analytics.TimeRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Playback clock plus a sliding window of strokes loaded ahead of it. */
class ReplayController(private val data: ReplayData, private val scope: CoroutineScope) {
    var timeline by mutableStateOf<ReplayTimeline?>(null)
        private set
    var playMs by mutableLongStateOf(0)
        private set
    var playing by mutableStateOf(false)
    var speed by mutableFloatStateOf(4f)
    var strokes by mutableStateOf<List<ReplayStroke>>(emptyList())
        private set

    private var loadedFrom = 0L
    private var loadedTo = 0L
    private var loadJob: Job? = null
    private var openJob: Job? = null

    fun open(range: TimeRange, condensed: Boolean) {
        playing = false
        openJob?.cancel()
        openJob = scope.launch {
            val t = withContext(Dispatchers.IO) { data.timeline(range, condensed) }
            timeline = t
            seek(0)
        }
    }

    fun seek(ms: Long) {
        val t = timeline ?: return
        playMs = ms.coerceIn(0, t.totalMs)
        loadJob?.cancel()
        strokes = emptyList()
        // Include strokes that began a little before the seek point and are still in progress.
        loadedFrom = t.wallAt(playMs) - LOOKBEHIND_MS
        loadedTo = loadedFrom
        ensureLoaded()
    }

    fun advance(frameMs: Double) {
        val t = timeline ?: return
        playMs = (playMs + (frameMs * speed).toLong()).coerceAtMost(t.totalMs)
        if (playMs >= t.totalMs) playing = false
        ensureLoaded()
    }

    private fun ensureLoaded() {
        val t = timeline ?: return
        if (loadJob?.isActive == true) return
        // Refill in chunks when less than REFILL_AT of (real-time) playback is buffered.
        if (loadedTo > t.wallAt(playMs + (REFILL_AT_MS * speed).toLong())) return
        val from = loadedTo
        val to = maxOf(t.wallAt(playMs + (CHUNK_MS * speed).toLong()), from + 1)
        loadJob = scope.launch {
            val more = withContext(Dispatchers.IO) { data.load(from, to) }
            val keepAfter = t.wallAt(playMs) - LOOKBEHIND_MS
            strokes = strokes.filter { it.endWallMs >= keepAfter } + more
            loadedTo = to
        }
    }

    private companion object {
        const val LOOKBEHIND_MS = 10_000L
        const val REFILL_AT_MS = 4_000L
        const val CHUNK_MS = 20_000L
    }
}
