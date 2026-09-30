package io.github.xxlinnix.swivel.core.virtualpad

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UhidTest {
    @Test
    fun create2MatchesTheKernelStructLayout() {
        val descriptor = byteArrayOf(1, 2, 3)
        val bytes = Uhid.create2("Pad", "swivel", "", descriptor, Uhid.BUS_VIRTUAL, 0x045E, 0x028E)
        assertEquals(280, Uhid.CREATE2_HEADER_SIZE) // The same header size scrcpy writes.
        assertEquals(280 + 3, bytes.size)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(11, b.getInt(0))
        assertEquals("Pad", String(bytes, 4, 3))
        assertEquals(0, bytes[4 + 3].toInt()) // NUL-terminated
        assertEquals("swivel", String(bytes, 4 + 128, 6))
        assertEquals(3, b.getShort(4 + 128 + 64 + 64).toInt())
        assertEquals(0x06, b.getShort(4 + 256 + 2).toInt())
        assertEquals(0x045E, b.getInt(4 + 256 + 4))
        assertEquals(0x028E, b.getInt(4 + 256 + 8))
        assertContentEquals(descriptor, bytes.copyOfRange(280, 283))
    }

    @Test
    fun aTooLongNameIsCutAndStillTerminated() {
        val bytes = Uhid.create2("x".repeat(300), "", "", byteArrayOf(), 6, 0, 0)
        assertEquals('x'.code, bytes[4 + 126].toInt())
        assertEquals(0, bytes[4 + 127].toInt())
    }

    @Test
    fun input2AndDestroy() {
        val report = ByteArray(15) { it.toByte() }
        val bytes = Uhid.input2(report)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(12, b.getInt(0))
        assertEquals(15, b.getShort(4).toInt())
        assertContentEquals(report, bytes.copyOfRange(6, 21))
        assertContentEquals(byteArrayOf(1, 0, 0, 0), Uhid.destroy())
    }
}
