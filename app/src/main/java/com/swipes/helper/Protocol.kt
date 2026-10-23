package com.swipes.helper

import java.io.DataInputStream
import java.io.DataOutputStream

/** Wire protocol between the shell-uid helper and the app over 127.0.0.1. */
object Protocol {
    /** Bump on any wire change; the app relaunches helpers that speak another version. */
    const val VERSION = 2
    val PORTS = 47321..47325
    const val TOKEN_HEX_LEN = 64

    // client -> helper
    const val MSG_HELLO = 1 // token, instanceId, resumeFromSeq
    const val MSG_SHUTDOWN = 2 // token

    // helper -> client
    const val MSG_INFO = 10
    const val MSG_SAMPLES = 11 // firstSeq, count, count * STRIDE longs
    const val MSG_REJECT = 12 // reason

    /** Longs per sample: [monoUs, wallUs, slot, trackingId (-1 = lift), x, y, touchMajor]. */
    const val STRIDE = 7
    const val F_MONO = 0
    const val F_WALL = 1
    const val F_SLOT = 2
    const val F_TRACKING_ID = 3
    const val F_X = 4
    const val F_Y = 5
    const val F_MAJOR = 6
}

data class HelperInfo(
    val version: Int,
    val instanceId: Long,
    /** APK the helper was started from; differs from the app's after an update. */
    val apkPath: String,
    val deviceName: String,
    val devicePath: String,
    val xMax: Int,
    val yMax: Int,
    val slots: Int,
    val oldestSeq: Long,
    val nextSeq: Long,
) {
    fun write(out: DataOutputStream) {
        out.writeInt(Protocol.MSG_INFO)
        out.writeInt(Protocol.VERSION)
        out.writeInt(version)
        out.writeLong(instanceId)
        out.writeUTF(apkPath)
        out.writeUTF(deviceName)
        out.writeUTF(devicePath)
        out.writeInt(xMax)
        out.writeInt(yMax)
        out.writeInt(slots)
        out.writeLong(oldestSeq)
        out.writeLong(nextSeq)
    }

    companion object {
        /** Reads the body after the MSG_INFO tag; null if the helper speaks another protocol version. */
        fun read(input: DataInputStream): HelperInfo? {
            if (input.readInt() != Protocol.VERSION) return null
            return HelperInfo(
                input.readInt(), input.readLong(), input.readUTF(), input.readUTF(), input.readUTF(),
                input.readInt(), input.readInt(), input.readInt(), input.readLong(), input.readLong(),
            )
        }
    }
}
