package com.swipecounter.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class ProtocolTest {
    private val info = HelperInfo(3, 42L, "/data/app/x/base.apk", "sec_touchscreen", "/dev/input/event8", 4095, 4095, 10, 5, 99)

    @Test
    fun `info round trips`() {
        val bytes = ByteArrayOutputStream().also { info.write(DataOutputStream(it)) }.toByteArray()
        val input = DataInputStream(ByteArrayInputStream(bytes))
        assertEquals(Protocol.MSG_INFO, input.readInt())
        assertEquals(info, HelperInfo.read(input))
    }

    @Test
    fun `other protocol version reads as null`() {
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(Protocol.VERSION + 1) }.toByteArray()
        assertNull(HelperInfo.read(DataInputStream(ByteArrayInputStream(bytes))))
    }
}
