package io.github.xxlinnix.swivel.ui.common

import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.core.model.ModeReason
import io.github.xxlinnix.swivel.core.model.ModeVerdict

fun modeLabel(mode: ControllerMode): String = when (mode) {
    ControllerMode.A -> "Mode A (MOGA mode)"
    ControllerMode.B -> "Mode B (standard gamepad)"
    ControllerMode.UNKNOWN -> "Mode unknown"
}

fun reasonLabel(reason: ModeReason): String = when (reason) {
    ModeReason.GAMEPAD_INPUT_DEVICE -> "connected as a gamepad"
    ModeReason.HID_SERVICE -> "offers the HID gamepad service"
    ModeReason.SERIAL_SERVICE_ONLY -> "offers only the MOGA serial service"
    ModeReason.NAME_HINT -> "judging by its name only"
    ModeReason.NO_EVIDENCE -> "no information yet"
}

fun verdictLabel(verdict: ModeVerdict): String = "${modeLabel(verdict.mode)}, ${reasonLabel(verdict.reason)}"

fun batteryLabel(battery: BatteryReading): String = when (battery) {
    BatteryReading.NotReported -> "Battery: not reported over this connection"
    is BatteryReading.Level -> {
        val percent = (battery.fraction * 100).toInt()
        val charging = when (battery.charging) {
            true -> ", charging"
            false -> ""
            null -> ""
        }
        "Battery: $percent%$charging"
    }
    is BatteryReading.LowFlag -> if (battery.low) "Battery: low" else "Battery: OK"
}

fun hex4(value: Int): String = "0x" + value.toString(16).uppercase().padStart(4, '0')
