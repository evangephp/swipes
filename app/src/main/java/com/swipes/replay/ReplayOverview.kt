package com.swipes.replay

/** A tally row: one app, all system UI/launchers together, or everything else. */
sealed interface TallyCategory {
    data class App(val pkg: String) : TallyCategory
    data object System : TallyCategory
    data object Other : TallyCategory
}

/**
 * Whole-range summary for the replay controls: touch volume per playback bin (the scrub bar's
 * histogram) and cumulative touches per tally category per bin (the side tally).
 */
class ReplayOverview(
    val timeline: ReplayTimeline,
    val volume: IntArray,
    val categories: List<TallyCategory>,
    /** cumulative[c][b] = touches in category c in bins 0..b. */
    private val cumulative: Array<IntArray>,
) {
    val bins get() = volume.size

    /** Touches per category from the start of the range up to [playMs], interpolated within a bin. */
    fun tallyAt(playMs: Long): IntArray {
        if (bins == 0 || timeline.totalMs <= 0) return IntArray(categories.size)
        val pos = playMs.toDouble() / timeline.totalMs * bins
        val b = pos.toInt().coerceIn(0, bins - 1)
        val frac = (pos - b).coerceIn(0.0, 1.0)
        return IntArray(categories.size) { c ->
            val before = if (b == 0) 0 else cumulative[c][b - 1]
            (before + (cumulative[c][b] - before) * frac).toInt()
        }
    }

    /** Collects strokes (in any order) and builds the overview. */
    class Builder(private val timeline: ReplayTimeline, private val bins: Int, private val isSystem: (String?) -> Boolean) {
        private val volume = IntArray(bins)
        private val perPackage = HashMap<String, IntArray>()
        private val system = IntArray(bins)
        private val unknown = IntArray(bins)

        fun add(startWallMs: Long, pkg: String?) {
            if (timeline.totalMs <= 0) return
            val b = (timeline.playAt(startWallMs).toDouble() / timeline.totalMs * bins).toInt().coerceIn(0, bins - 1)
            volume[b]++
            when {
                isSystem(pkg) -> system[b]++
                pkg == null -> unknown[b]++
                else -> perPackage.getOrPut(pkg) { IntArray(bins) }[b]++
            }
        }

        fun build(topApps: Int = 6): ReplayOverview {
            val ranked = perPackage.entries.sortedWith(compareByDescending<Map.Entry<String, IntArray>> { it.value.sum() }.thenBy { it.key })
            val top = ranked.take(topApps)
            val other = unknown.copyOf()
            for ((_, counts) in ranked.drop(topApps)) for (b in 0 until bins) other[b] += counts[b]
            val categories = top.map { TallyCategory.App(it.key) } + TallyCategory.System + TallyCategory.Other
            val rows = top.map { it.value } + listOf(system, other)
            val cumulative = Array(rows.size) { c ->
                IntArray(bins).also { acc ->
                    var sum = 0
                    for (b in 0 until bins) {
                        sum += rows[c][b]
                        acc[b] = sum
                    }
                }
            }
            return ReplayOverview(timeline, volume, categories, cumulative)
        }
    }
}
