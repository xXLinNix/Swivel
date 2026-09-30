package io.github.xxlinnix.swivel.core.virtualpad

import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.model.GamepadButton
import kotlin.math.roundToInt

/**
 * The virtual gamepad Swivel shows to Android: an HID descriptor and its 15-byte report.
 *
 * The layout is the one scrcpy uses for its UHID gamepads (Apache-2.0,
 * github.com/Genymobile/scrcpy, app/src/hid/hid_gamepad.c), paired with the Xbox 360
 * controller's ids. Android ships a key layout for that controller
 * (Vendor_045e_Product_028e.kl) that maps these usages to Android's standard gamepad
 * axes and buttons, and games' controller databases know it too (D-025).
 *
 * Report: left stick X, Y and right stick X, Y as 16-bit 0..65535; L2, R2 as 16-bit
 * 0..32767; 16 button bits; a 4-bit hat switch for the D-pad. All little-endian.
 */
object XboxPadReport {
    const val VENDOR_ID = 0x045E
    const val PRODUCT_ID = 0x028E
    const val REPORT_SIZE = 15

    val DESCRIPTOR: ByteArray = bytes(
        0x05, 0x01, //       Usage Page (Generic Desktop)
        0x09, 0x05, //       Usage (Gamepad)
        0xA1, 0x01, //       Collection (Application)
        0xA1, 0x00, //         Collection (Physical)
        0x05, 0x01, //           Usage Page (Generic Desktop)
        0x09, 0x30, //           Usage (X): left stick X
        0x09, 0x31, //           Usage (Y): left stick Y
        0x09, 0x33, //           Usage (Rx): right stick X
        0x09, 0x34, //           Usage (Ry): right stick Y
        0x15, 0x00, //           Logical Minimum (0)
        0x27, 0xFF, 0xFF, 0x00, 0x00, // Logical Maximum (65535)
        0x75, 0x10, //           Report Size (16)
        0x95, 0x04, //           Report Count (4)
        0x81, 0x02, //           Input (Data, Variable, Absolute)
        0x05, 0x01, //           Usage Page (Generic Desktop)
        0x09, 0x32, //           Usage (Z): L2
        0x09, 0x35, //           Usage (Rz): R2
        0x15, 0x00, //           Logical Minimum (0)
        0x26, 0xFF, 0x7F, //     Logical Maximum (32767)
        0x75, 0x10, //           Report Size (16)
        0x95, 0x02, //           Report Count (2)
        0x81, 0x02, //           Input (Data, Variable, Absolute)
        0x05, 0x09, //           Usage Page (Button)
        0x19, 0x01, //           Usage Minimum (1)
        0x29, 0x10, //           Usage Maximum (16)
        0x15, 0x00, //           Logical Minimum (0)
        0x25, 0x01, //           Logical Maximum (1)
        0x95, 0x10, //           Report Count (16)
        0x75, 0x01, //           Report Size (1)
        0x81, 0x02, //           Input (Data, Variable, Absolute)
        0x05, 0x01, //           Usage Page (Generic Desktop)
        0x09, 0x39, //           Usage (Hat switch): D-pad
        0x15, 0x01, //           Logical Minimum (1)
        0x25, 0x08, //           Logical Maximum (8)
        0x75, 0x04, //           Report Size (4)
        0x95, 0x01, //           Report Count (1)
        0x81, 0x42, //           Input (Data, Variable, Null State)
        0xC0, //                 End Collection
        0xC0, //               End Collection
    )

    /**
     * Button bits, in the order Linux assigns gamepad buttons (BTN_A, BTN_B, BTN_C, BTN_X,
     * BTN_Y, BTN_Z, BTN_TL, BTN_TR, BTN_TL2, BTN_TR2, BTN_SELECT, BTN_START, BTN_MODE,
     * BTN_THUMBL, BTN_THUMBR). C, Z and Mode are never pressed.
     */
    private val buttonBits = mapOf(
        GamepadButton.A to 0,
        GamepadButton.B to 1,
        GamepadButton.X to 3,
        GamepadButton.Y to 4,
        GamepadButton.L1 to 6,
        GamepadButton.R1 to 7,
        GamepadButton.L2 to 8,
        GamepadButton.R2 to 9,
        GamepadButton.SELECT to 10,
        GamepadButton.START to 11,
        GamepadButton.L3 to 13,
        GamepadButton.R3 to 14,
    )

    fun encode(snapshot: ControllerSnapshot): ByteArray {
        val report = ByteArray(REPORT_SIZE)
        putShort(report, 0, stick(snapshot.leftStick.x))
        putShort(report, 2, stick(snapshot.leftStick.y))
        putShort(report, 4, stick(snapshot.rightStick.x))
        putShort(report, 6, stick(snapshot.rightStick.y))
        putShort(report, 8, trigger(snapshot.leftTrigger))
        putShort(report, 10, trigger(snapshot.rightTrigger))
        var buttons = 0
        for ((button, bit) in buttonBits) if (button in snapshot.pressed) buttons = buttons or (1 shl bit)
        putShort(report, 12, buttons)
        report[14] = hat(snapshot.pressed).toByte()
        return report
    }

    /** -1..1 (Android's convention, up is -1) to 0..65535 with 32768 at rest. */
    fun stick(value: Float): Int = ((value.coerceIn(-1f, 1f) + 1f) * 32767.5f).roundToInt().coerceIn(0, 65535)

    fun trigger(value: Float): Int = (value.coerceIn(0f, 1f) * 32767f).roundToInt()

    /** 0 when released, else 1 (up) clockwise to 8 (up-left). Opposite directions cancel. */
    fun hat(pressed: Set<GamepadButton>): Int {
        val up = GamepadButton.DPAD_UP in pressed && GamepadButton.DPAD_DOWN !in pressed
        val down = GamepadButton.DPAD_DOWN in pressed && GamepadButton.DPAD_UP !in pressed
        val left = GamepadButton.DPAD_LEFT in pressed && GamepadButton.DPAD_RIGHT !in pressed
        val right = GamepadButton.DPAD_RIGHT in pressed && GamepadButton.DPAD_LEFT !in pressed
        return when {
            up && right -> 2
            down && right -> 4
            down && left -> 6
            up && left -> 8
            up -> 1
            right -> 3
            down -> 5
            left -> 7
            else -> 0
        }
    }

    private fun putShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
