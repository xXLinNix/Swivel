package io.github.xxlinnix.swivel.core.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModeAFrameParserTest {
    private val a = firstGen(buttons = 0x04)
    private val b = secondGen(l2 = 0x80)

    @Test
    fun oneWholeFrame() {
        val frames = ModeAFrameParser().feed(a)
        assertEquals(1, frames.size)
        assertContentEquals(a, frames[0].bytes)
        assertEquals(100, frames[0].code)
        assertEquals(1, frames[0].controllerId)
    }

    @Test
    fun framesGluedTogetherOfBothLengths() {
        val frames = ModeAFrameParser().feed(a + b + a)
        assertEquals(listOf(12, 14, 12), frames.map { it.bytes.size })
    }

    @Test
    fun aFrameSplitAcrossReads() {
        val parser = ModeAFrameParser()
        assertTrue(parser.feed(a, 0, 1).isEmpty())
        assertTrue(parser.feed(a, 1, 6).isEmpty())
        val frames = parser.feed(a, 7, 5)
        assertEquals(1, frames.size)
        assertContentEquals(a, frames[0].bytes)
    }

    @Test
    fun noiseBeforeAFrameIsSkipped() {
        val parser = ModeAFrameParser()
        val frames = parser.feed(byteArrayOf(0x00, 0x11, 0x7A, 0x7F) + a)
        assertEquals(1, frames.size)
        assertEquals(4, parser.skippedBytes)
    }

    @Test
    fun aCorruptFrameCostsOnlyItself() {
        val corrupt = a.copyOf().also { it[6] = 0x33 }
        val parser = ModeAFrameParser()
        val frames = parser.feed(corrupt + b)
        assertEquals(1, frames.size)
        assertContentEquals(b, frames[0].bytes)
        assertEquals(1, parser.checksumErrors)
    }

    @Test
    fun anImpossibleLengthIsSkipped() {
        val frames = ModeAFrameParser().feed(byteArrayOf(0x7A, 0x02) + a)
        assertEquals(1, frames.size)
    }

    @Test
    fun hexIsSpaceSeparatedUpperCase() {
        assertEquals("7A 0C 64 01", ModeAFrame(a).hex().take(11))
    }
}
