package io.github.xxlinnix.swivel.core.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModeAProtocolTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun commandsMatchTheDocumentedBytes() {
        // 0x5A ^ 0x05 ^ 0x43 ^ 0x01 = 0x1D
        assertContentEquals(bytes(0x5A, 0x05, 0x43, 0x01, 0x1D), ModeAProtocol.command(ModeAProtocol.SET_CONTROLLER_ID, 1))
        // 0x5A ^ 0x05 ^ 0x44 ^ 0x01 = 0x1A
        assertContentEquals(bytes(0x5A, 0x05, 0x44, 0x01, 0x1A), ModeAProtocol.command(ModeAGeneration.FIRST.listenCommand, 1))
        // 0x5A ^ 0x05 ^ 0x46 ^ 0x02 = 0x1B
        assertContentEquals(bytes(0x5A, 0x05, 0x46, 0x02, 0x1B), ModeAProtocol.command(ModeAGeneration.SECOND.listenCommand, 2))
    }

    @Test
    fun generationsHaveTheDocumentedCodes() {
        assertEquals(listOf(65, 68, 97, 100, 12), ModeAGeneration.FIRST.let { listOf(it.pollCommand, it.listenCommand, it.pollReply, it.listenReply, it.reportLength) })
        assertEquals(listOf(69, 70, 101, 102, 14), ModeAGeneration.SECOND.let { listOf(it.pollCommand, it.listenCommand, it.pollReply, it.listenReply, it.reportLength) })
        assertEquals(ModeAGeneration.SECOND, ModeAGeneration.FIRST.other)
        assertEquals(ModeAGeneration.FIRST, ModeAGeneration.forReply(97))
        assertEquals(ModeAGeneration.SECOND, ModeAGeneration.forReply(102))
        assertNull(ModeAGeneration.forReply(99))
    }
}
