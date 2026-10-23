package com.swipes.helper

import android.util.Log
import com.swipes.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Entry point of the shell-uid helper, started by the app over adb via `app_process`.
 * Reads the touchscreen evdev node, buffers samples, and serves them to the app on loopback.
 * The auth token arrives as the first line on stdin so it never appears in argv or env.
 */
object HelperMain {
    private const val TAG = "SwipeHelper"
    private const val RING_CAPACITY = 100_000 // ~7 min of one finger at 240 Hz
    private const val MAX_FRAME = 2048

    private val instanceId = SecureRandom().nextLong()
    private lateinit var token: ByteArray
    private val ring = SampleRing(RING_CAPACITY)

    @Volatile private var client: Socket? = null

    @JvmStatic
    fun main(args: Array<String>) {
        token = (readlnOrNull()?.trim().orEmpty()).toByteArray()
        if (token.size != Protocol.TOKEN_HEX_LEN) fail("missing token on stdin")

        val device = Evdev.findTouchscreen() ?: fail("no touchscreen found")
        val server = bind() ?: fail("no free port in ${Protocol.PORTS}")
        Log.i(TAG, "v${BuildConfig.VERSION_CODE} serving ${device.name} (${device.path}) on ${server.localPort}")

        thread(name = "evdev") {
            runCatching { readEvdev(device) }.onFailure { Log.e(TAG, "evdev reader died", it) }
            exitProcess(2)
        }
        while (true) {
            val socket = server.accept()
            thread(name = "client") {
                // An uncaught exception would kill the whole helper (and its buffer) on a client drop.
                try {
                    serve(socket, device)
                } catch (e: Exception) {
                    Log.d(TAG, "client ended: $e")
                }
            }
        }
    }

    private fun fail(message: String): Nothing {
        Log.e(TAG, message)
        System.err.println(message)
        exitProcess(1)
    }

    private fun bind(): ServerSocket? {
        val loopback = InetAddress.getLoopbackAddress()
        for (port in Protocol.PORTS) {
            runCatching { return ServerSocket(port, 4, loopback) }
        }
        return null
    }

    private fun readEvdev(device: TouchDevice) {
        val raw = ByteArray(Evdev.EVENT_SIZE * 64)
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.nativeOrder())
        var monoOffsetUs = 0L
        val parser = MtParser(device.slots.coerceIn(1, 32)) { wallUs, slot, tid, x, y, major ->
            ring.add(wallUs - monoOffsetUs, wallUs, slot, tid, x, y, major)
        }
        FileInputStream(device.path).use { input ->
            while (true) {
                val n = input.read(raw)
                if (n <= 0) return
                // evdev timestamps are CLOCK_REALTIME; System.nanoTime() is CLOCK_MONOTONIC,
                // the clock behind MotionEvent and AccessibilityEvent times.
                monoOffsetUs = System.currentTimeMillis() * 1000 - System.nanoTime() / 1000
                var off = 0
                while (off + Evdev.EVENT_SIZE <= n) {
                    val wallUs = buf.getLong(off) * 1_000_000 + buf.getLong(off + 8)
                    val type = buf.getShort(off + 16).toInt() and 0xffff
                    val code = buf.getShort(off + 18).toInt() and 0xffff
                    parser.onEvent(wallUs, type, code, buf.getInt(off + 20))
                    off += Evdev.EVENT_SIZE
                }
                ring.publish()
            }
        }
    }

    private fun serve(socket: Socket, device: TouchDevice) {
        socket.use {
            socket.tcpNoDelay = true
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val type = input.readInt()
            val authorized = MessageDigest.isEqual(input.readUTF().toByteArray(), token)
            if (!authorized) {
                out.writeInt(Protocol.MSG_REJECT)
                out.writeUTF("bad token")
                out.flush()
                return
            }
            if (type == Protocol.MSG_SHUTDOWN) {
                Log.i(TAG, "shutdown requested")
                exitProcess(0)
            }
            if (type != Protocol.MSG_HELLO) return
            val clientInstance = input.readLong()
            val resumeFrom = input.readLong()
            var cursor = if (clientInstance == instanceId) resumeFrom else 0L

            // One consumer at a time: a new authorized client replaces the previous one.
            client?.let { runCatching { it.close() } }
            client = socket

            HelperInfo(
                BuildConfig.VERSION_CODE, instanceId, System.getProperty("java.class.path").orEmpty(),
                device.name, device.path, device.xMax, device.yMax, device.slots, ring.oldestSeq(), ring.nextSeq(),
            ).write(out)
            out.flush()

            while (client === socket) {
                ring.awaitAfter(cursor, 1000)
                val (first, data) = ring.copy(cursor, MAX_FRAME)
                val count = data.size / Protocol.STRIDE
                if (count == 0 && first == cursor) continue
                out.writeInt(Protocol.MSG_SAMPLES)
                out.writeLong(first)
                out.writeInt(count)
                for (v in data) out.writeLong(v)
                out.flush()
                cursor = first + count
            }
        }
    }
}
