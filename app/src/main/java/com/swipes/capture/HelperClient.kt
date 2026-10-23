package com.swipes.capture

import android.util.Log
import com.swipes.helper.HelperInfo
import com.swipes.helper.Protocol
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/** Connects to the helper on loopback, authenticates, and streams samples with resume-by-seq. */
class HelperClient(private val token: String, private val listener: Listener) {

    interface Listener {
        fun onConnected(info: HelperInfo)

        /** The running helper speaks another protocol version and must be relaunched. */
        fun onIncompatibleHelper()

        /**
         * [firstSeq] > the requested seq means samples were lost (ring overrun or helper restart).
         * [samples] is reused after this returns.
         */
        fun onSamples(firstSeq: Long, count: Int, samples: LongArray)

        /** [helperMissing] = nothing is listening, as opposed to a dropped connection. */
        fun onDisconnected(reason: String, helperMissing: Boolean)
    }

    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    private var worker: Thread? = null

    private var instanceId = 0L
    private var nextSeq = 0L
    private var backoffMs = MIN_BACKOFF_MS

    /** Sets where the next connection resumes, e.g. from the last durably flushed sample. */
    @Synchronized
    fun resumeFrom(instanceId: Long, seq: Long) {
        this.instanceId = instanceId
        nextSeq = seq
    }

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "helper-client") { loop() }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        worker?.interrupt()
        worker = null
    }

    /** Asks a running helper to exit, e.g. before launching a newer version. */
    fun requestShutdown() {
        for (port in Protocol.PORTS) {
            runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 300)
                    DataOutputStream(s.getOutputStream()).apply {
                        writeInt(Protocol.MSG_SHUTDOWN)
                        writeUTF(token)
                        flush()
                    }
                }
            }
        }
    }

    private fun loop() {
        while (running) {
            var missing = false
            val reason = try {
                missing = !connectAndStream()
                if (missing) "helper not running" else "closed"
            } catch (e: IOException) {
                e.message ?: e.toString()
            }
            if (!running) break
            listener.onDisconnected(reason, missing)
            try {
                Thread.sleep(backoffMs)
            } catch (_: InterruptedException) {
                break
            }
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** Returns false if no helper is listening; otherwise streams until the connection ends. */
    private fun connectAndStream(): Boolean {
        val s = connectAny() ?: return false
        socket = s
        s.use {
            s.tcpNoDelay = true
            val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(s.getInputStream()))
            val (resumeInstance, resumeSeq) = synchronized(this) { instanceId to nextSeq }
            out.writeInt(Protocol.MSG_HELLO)
            out.writeUTF(token)
            out.writeLong(resumeInstance)
            out.writeLong(resumeSeq)
            out.flush()

            when (val type = input.readInt()) {
                Protocol.MSG_INFO -> Unit
                Protocol.MSG_REJECT -> throw IOException("rejected: ${input.readUTF()}")
                else -> throw IOException("unexpected message $type")
            }
            val info = HelperInfo.read(input)
            if (info == null) {
                listener.onIncompatibleHelper()
                throw IOException("incompatible helper")
            }
            synchronized(this) {
                if (info.instanceId != instanceId) {
                    instanceId = info.instanceId
                    nextSeq = 0
                }
            }
            backoffMs = MIN_BACKOFF_MS
            listener.onConnected(info)

            var buf = LongArray(0)
            while (running) {
                val type = input.readInt()
                if (type != Protocol.MSG_SAMPLES) throw IOException("unexpected message $type")
                val first = input.readLong()
                val count = input.readInt()
                val n = count * Protocol.STRIDE
                if (buf.size < n) buf = LongArray(n)
                for (i in 0 until n) buf[i] = input.readLong()
                synchronized(this) { nextSeq = first + count }
                listener.onSamples(first, count, buf)
            }
        }
        return true
    }

    private fun connectAny(): Socket? {
        for (port in Protocol.PORTS) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 300)
                return s
            } catch (e: IOException) {
                s.close()
            }
        }
        Log.d(TAG, "no helper on ${Protocol.PORTS}")
        return null
    }

    private companion object {
        const val TAG = "HelperClient"
        const val MIN_BACKOFF_MS = 500L
        const val MAX_BACKOFF_MS = 10_000L
    }
}
