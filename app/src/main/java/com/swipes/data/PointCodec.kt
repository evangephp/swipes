package com.swipes.data

import com.swipes.capture.POINT_STRIDE
import java.io.ByteArrayOutputStream

/**
 * Compact point encoding for stroke BLOBs: varints of per-point deltas [dtUs, dx, dy, dMajor].
 * dt is already a non-negative delta (plain varint); the rest are zigzag. ~5 bytes per point at 120 Hz.
 */
object PointCodec {
    fun encode(points: IntArray): ByteArray {
        val out = ByteArrayOutputStream(points.size * 2)
        val prev = IntArray(POINT_STRIDE)
        var i = 0
        while (i < points.size) {
            writeVarint(out, points[i])
            for (f in 1 until POINT_STRIDE) {
                writeVarint(out, zigzag(points[i + f] - prev[f]))
                prev[f] = points[i + f]
            }
            i += POINT_STRIDE
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray, pointCount: Int): IntArray {
        val points = IntArray(pointCount * POINT_STRIDE)
        val prev = IntArray(POINT_STRIDE)
        var pos = 0
        fun varint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = bytes[pos++].toInt()
                result = result or ((b and 0x7f) shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        for (p in 0 until pointCount) {
            val o = p * POINT_STRIDE
            points[o] = varint()
            for (f in 1 until POINT_STRIDE) {
                val z = varint()
                prev[f] += (z ushr 1) xor -(z and 1)
                points[o + f] = prev[f]
            }
        }
        return points
    }

    private fun zigzag(v: Int) = (v shl 1) xor (v shr 31)

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v and 0x7f.inv() != 0) {
            out.write((v and 0x7f) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }
}
