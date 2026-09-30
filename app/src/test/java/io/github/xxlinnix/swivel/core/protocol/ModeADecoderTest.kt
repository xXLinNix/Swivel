package io.github.xxlinnix.swivel.core.protocol

import io.github.xxlinnix.swivel.core.model.GamepadButton
import io.github.xxlinnix.swivel.core.model.Stick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModeADecoderTest {
    private fun decode(bytes: ByteArray) = assertNotNull(ModeADecoder.decode(ModeAFrame(bytes)))

    @Test
    fun everyButtonBitMapsToItsButton() {
        val expected = listOf(
            GamepadButton.Y, GamepadButton.B, GamepadButton.A, GamepadButton.X,
            GamepadButton.START, GamepadButton.SELECT, GamepadButton.L1, GamepadButton.R1,
        )
        expected.forEachIndexed { bit, button ->
            assertEquals(setOf(button), decode(firstGen(buttons = 1 shl bit)).snapshot.pressed, "bit $bit")
        }
        val pad = listOf(
            GamepadButton.DPAD_UP, GamepadButton.DPAD_DOWN, GamepadButton.DPAD_LEFT, GamepadButton.DPAD_RIGHT,
            GamepadButton.L2, GamepadButton.R2, GamepadButton.L3, GamepadButton.R3,
        )
        pad.forEachIndexed { bit, button ->
            assertEquals(setOf(button), decode(secondGen(pad = 1 shl bit)).snapshot.pressed, "pad bit $bit")
        }
    }

    @Test
    fun everythingPressedAtOnce() {
        assertEquals(GamepadButton.entries.toSet(), decode(secondGen(buttons = 0xFF, pad = 0xFF)).snapshot.pressed)
    }

    @Test
    fun sticksAreSignedAndYIsFlippedToAndroidsConvention() {
        val report = decode(firstGen(lx = 127, ly = 127, rx = 0x81, ry = 0x80))
        assertEquals(Stick(1f, -1f), report.snapshot.leftStick)
        assertEquals(-127 / 127f, report.snapshot.rightStick.x)
        assertEquals(1f, report.snapshot.rightStick.y) // -128 clamps to -1, then flips
    }

    @Test
    fun aCentredStickReadsPlusZeroNotMinusZero() {
        // Regression: flipping Y turned 0 into -0.0, which the test screen showed as "-0.000".
        val report = decode(firstGen())
        assertEquals(Stick(0f, 0f), report.snapshot.leftStick)
        assertEquals(Stick(0f, 0f), report.snapshot.rightStick)
    }

    @Test
    fun secondGenerationTriggersAreAnalog() {
        val report = decode(secondGen(l2 = 255, r2 = 0x80))
        assertEquals(ModeAGeneration.SECOND, report.generation)
        assertEquals(1f, report.snapshot.leftTrigger)
        assertEquals(128 / 255f, report.snapshot.rightTrigger)
    }

    @Test
    fun firstGenerationTriggersFollowTheirButtons() {
        val report = decode(firstGen(pad = 0x10))
        assertEquals(ModeAGeneration.FIRST, report.generation)
        assertEquals(1f, report.snapshot.leftTrigger)
        assertEquals(0f, report.snapshot.rightTrigger)
    }

    @Test
    fun powerByteGivesLowBatteryAndVersion() {
        val fine = decode(firstGen(power = 0x10))
        assertFalse(fine.lowBattery)
        assertEquals(1, fine.version)
        val low = decode(secondGen(power = 0x11))
        assertTrue(low.lowBattery)
        assertEquals(1, low.version)
    }

    @Test
    fun pollRepliesDecodeLikeStreamReports() {
        assertEquals(setOf(GamepadButton.A), decode(firstGen(buttons = 0x04, code = 97)).snapshot.pressed)
        assertEquals(setOf(GamepadButton.A), decode(secondGen(buttons = 0x04, code = 101)).snapshot.pressed)
    }

    @Test
    fun unknownCodesAndWrongLengthsAreRejected() {
        assertNull(ModeADecoder.decode(ModeAFrame(report(99, 0, 0, 0, 0, 0, 0, 0x10))))
        // A 12-byte frame claiming to be second generation.
        assertNull(ModeADecoder.decode(ModeAFrame(report(102, 0, 0, 0, 0, 0, 0, 0x10))))
    }
}
