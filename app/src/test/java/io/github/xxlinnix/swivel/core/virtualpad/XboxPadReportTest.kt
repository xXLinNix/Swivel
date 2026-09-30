package io.github.xxlinnix.swivel.core.virtualpad

import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.model.GamepadButton
import io.github.xxlinnix.swivel.core.model.Stick
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class XboxPadReportTest {
    private fun u16(report: ByteArray, offset: Int) =
        (report[offset].toInt() and 0xFF) or ((report[offset + 1].toInt() and 0xFF) shl 8)

    @Test
    fun aRestingControllerIsCentredWithNothingPressed() {
        val report = XboxPadReport.encode(ControllerSnapshot())
        assertEquals(XboxPadReport.REPORT_SIZE, report.size)
        for (offset in listOf(0, 2, 4, 6)) assertEquals(32768, u16(report, offset), "stick at $offset")
        assertEquals(0, u16(report, 8))
        assertEquals(0, u16(report, 10))
        assertEquals(0, u16(report, 12))
        assertEquals(0, report[14].toInt())
    }

    @Test
    fun sticksCoverTheFullRangeWithUpAtZero() {
        val report = XboxPadReport.encode(ControllerSnapshot(leftStick = Stick(-1f, -1f), rightStick = Stick(1f, 1f)))
        assertEquals(0, u16(report, 0))
        assertEquals(0, u16(report, 2)) // Up is the HID minimum, as Android expects.
        assertEquals(65535, u16(report, 4))
        assertEquals(65535, u16(report, 6))
        assertEquals(0, XboxPadReport.stick(-5f))
        assertEquals(65535, XboxPadReport.stick(5f))
    }

    @Test
    fun triggersScaleTo32767() {
        val report = XboxPadReport.encode(ControllerSnapshot(leftTrigger = 1f, rightTrigger = 0.5f))
        assertEquals(32767, u16(report, 8))
        assertEquals(16384, u16(report, 10))
    }

    @Test
    fun buttonsUseLinuxGamepadOrder() {
        fun bits(vararg buttons: GamepadButton) = u16(XboxPadReport.encode(ControllerSnapshot(keyButtons = buttons.toSet())), 12)
        assertEquals(1 shl 0, bits(GamepadButton.A))
        assertEquals(1 shl 1, bits(GamepadButton.B))
        assertEquals(1 shl 3, bits(GamepadButton.X))
        assertEquals(1 shl 4, bits(GamepadButton.Y))
        assertEquals(1 shl 6, bits(GamepadButton.L1))
        assertEquals(1 shl 7, bits(GamepadButton.R1))
        assertEquals(1 shl 10, bits(GamepadButton.SELECT))
        assertEquals(1 shl 11, bits(GamepadButton.START))
        assertEquals(1 shl 13, bits(GamepadButton.L3))
        assertEquals(1 shl 14, bits(GamepadButton.R3))
        assertEquals(0b0110_1111_1101_1011, bits(*GamepadButton.entries.toTypedArray()))
    }

    @Test
    fun theDpadBecomesAHatSwitch() {
        fun hat(vararg buttons: GamepadButton) = XboxPadReport.hat(buttons.toSet())
        assertEquals(0, hat())
        assertEquals(1, hat(GamepadButton.DPAD_UP))
        assertEquals(2, hat(GamepadButton.DPAD_UP, GamepadButton.DPAD_RIGHT))
        assertEquals(3, hat(GamepadButton.DPAD_RIGHT))
        assertEquals(4, hat(GamepadButton.DPAD_DOWN, GamepadButton.DPAD_RIGHT))
        assertEquals(5, hat(GamepadButton.DPAD_DOWN))
        assertEquals(6, hat(GamepadButton.DPAD_DOWN, GamepadButton.DPAD_LEFT))
        assertEquals(7, hat(GamepadButton.DPAD_LEFT))
        assertEquals(8, hat(GamepadButton.DPAD_UP, GamepadButton.DPAD_LEFT))
        assertEquals(0, hat(GamepadButton.DPAD_UP, GamepadButton.DPAD_DOWN))
    }

    @Test
    fun theDescriptorIsScrcpysGamepadLayout() {
        // Starts Generic Desktop / Gamepad / Application collection, ends with two End Collections.
        assertContentEquals(byteArrayOf(0x05, 0x01, 0x09, 0x05, 0xA1.toByte(), 0x01), XboxPadReport.DESCRIPTOR.copyOf(6))
        assertEquals(listOf(0xC0.toByte(), 0xC0.toByte()), XboxPadReport.DESCRIPTOR.takeLast(2))
        // 4x16 + 2x16 + 16x1 + 4 bits of hat = 116 bits of data, padded to 15 bytes.
        assertEquals(15, (4 * 16 + 2 * 16 + 16 + 4 + 7) / 8)
    }
}
