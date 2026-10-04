package com.swipecounter.data

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Touch counts on a fixed grid over the panel (natural orientation), stored compressed per hour. */
class HeatGrid(val counts: IntArray = IntArray(WIDTH * HEIGHT)) {

    fun add(panelX: Int, panelY: Int, panelMaxX: Int, panelMaxY: Int) {
        val cx = (panelX.toLong() * WIDTH / (panelMaxX + 1)).toInt().coerceIn(0, WIDTH - 1)
        val cy = (panelY.toLong() * HEIGHT / (panelMaxY + 1)).toInt().coerceIn(0, HEIGHT - 1)
        counts[cy * WIDTH + cx]++
    }

    fun addAll(other: HeatGrid) {
        for (i in counts.indices) counts[i] += other.counts[i]
    }

    fun isEmpty() = counts.all { it == 0 }

    fun encode(): ByteArray {
        val raw = ByteBuffer.allocate(counts.size * 4).apply { asIntBuffer().put(counts) }.array()
        val deflater = Deflater(Deflater.BEST_SPEED).apply {
            setInput(raw)
            finish()
        }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return out.toByteArray()
    }

    companion object {
        const val WIDTH = 64
        const val HEIGHT = 128

        fun decode(bytes: ByteArray): HeatGrid {
            val raw = ByteArray(WIDTH * HEIGHT * 4)
            Inflater().apply {
                setInput(bytes)
                inflate(raw)
                end()
            }
            val counts = IntArray(WIDTH * HEIGHT)
            ByteBuffer.wrap(raw).asIntBuffer().get(counts)
            return HeatGrid(counts)
        }
    }
}
