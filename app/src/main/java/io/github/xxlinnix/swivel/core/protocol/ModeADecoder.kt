package io.github.xxlinnix.swivel.core.protocol

import io.github.xxlinnix.swivel.core.hid.AndroidAxes
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.model.GamepadButton
import io.github.xxlinnix.swivel.core.model.Stick

/** A decoded report: the controls, plus what the power byte says. */
data class ModeAReport(
    val generation: ModeAGeneration,
    val snapshot: ControllerSnapshot,
    val lowBattery: Boolean,
    /** The high nibble of the power byte. Pivot called it the version. */
    val version: Int,
)

/**
 * Turns a report into a [ControllerSnapshot], so the test screen treats Mode A and
 * Mode B alike.
 *
 * Byte 4 holds Y B A X Start Select L1 R1 in bits 0 to 7. Byte 5 holds up, down, left,
 * right, L2, R2, L3, R3. Bytes 6 to 9 are the sticks as signed bytes, and bytes 10 and 11
 * the analog triggers on second-generation controllers. The byte before the checksum is
 * power: bit 0 set means the battery is low.
 *
 * Stick Y is inverted to Android's convention (up is -1), following moga-uinput. This
 * still has to be confirmed on hardware; the test screen shows the raw bytes to check.
 */
object ModeADecoder {
    private val buttonBits = listOf(
        GamepadButton.Y, GamepadButton.B, GamepadButton.A, GamepadButton.X,
        GamepadButton.START, GamepadButton.SELECT, GamepadButton.L1, GamepadButton.R1,
    )
    private val padBits = listOf(
        GamepadButton.DPAD_UP, GamepadButton.DPAD_DOWN, GamepadButton.DPAD_LEFT, GamepadButton.DPAD_RIGHT,
        GamepadButton.L2, GamepadButton.R2, GamepadButton.L3, GamepadButton.R3,
    )

    fun decode(frame: ModeAFrame): ModeAReport? {
        val generation = ModeAGeneration.forReply(frame.code) ?: return null
        val b = frame.bytes
        if (b.size != generation.reportLength) return null

        val pressed = bitsToButtons(b[4], buttonBits) + bitsToButtons(b[5], padBits)
        val leftTrigger: Float
        val rightTrigger: Float
        if (generation == ModeAGeneration.SECOND) {
            leftTrigger = unsigned(b[10]) / 255f
            rightTrigger = unsigned(b[11]) / 255f
        } else {
            leftTrigger = if (GamepadButton.L2 in pressed) 1f else 0f
            rightTrigger = if (GamepadButton.R2 in pressed) 1f else 0f
        }
        val left = Stick(signed(b[6]), -signed(b[7]))
        val right = Stick(signed(b[8]), -signed(b[9]))
        val power = unsigned(b[b.size - 2])

        val snapshot = ControllerSnapshot(
            keyButtons = pressed,
            leftStick = left,
            rightStick = right,
            leftTrigger = leftTrigger,
            rightTrigger = rightTrigger,
            rawAxes = mapOf(
                AndroidAxes.X to left.x,
                AndroidAxes.Y to left.y,
                AndroidAxes.Z to right.x,
                AndroidAxes.RZ to right.y,
                AndroidAxes.LTRIGGER to leftTrigger,
                AndroidAxes.RTRIGGER to rightTrigger,
            ),
        )
        return ModeAReport(generation, snapshot, lowBattery = power and 1 != 0, version = power shr 4)
    }

    private fun bitsToButtons(byte: Byte, names: List<GamepadButton>): Set<GamepadButton> {
        val value = unsigned(byte)
        return names.filterIndexed { bit, _ -> value and (1 shl bit) != 0 }.toSet()
    }

    private fun unsigned(byte: Byte): Int = byte.toInt() and 0xFF

    /** -128..127 to -1..1. Pivot normalised by 127 as well. */
    private fun signed(byte: Byte): Float = (byte.toInt() / 127f).coerceIn(-1f, 1f)
}
