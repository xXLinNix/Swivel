package io.github.xxlinnix.swivel.ui.common

import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.GamepadButton
import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.core.model.ModeReason
import io.github.xxlinnix.swivel.core.model.ModeVerdict
import io.github.xxlinnix.swivel.data.modea.ModeAState
import io.github.xxlinnix.swivel.data.modea.StopReason

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

fun buttonLabel(button: GamepadButton): String = when (button) {
    GamepadButton.DPAD_UP -> "Up"
    GamepadButton.DPAD_DOWN -> "Down"
    GamepadButton.DPAD_LEFT -> "Left"
    GamepadButton.DPAD_RIGHT -> "Right"
    GamepadButton.START -> "Start"
    GamepadButton.SELECT -> "Select"
    else -> button.name
}

fun modeAStatus(state: ModeAState): String = when (state) {
    ModeAState.Idle -> "Not connected"
    is ModeAState.Connecting -> if (state.attempt > 1) "Connecting (attempt ${state.attempt})…" else "Connecting…"
    is ModeAState.Connected -> "Connected in Mode A"
    is ModeAState.Waiting -> "Connection lost. Trying again in ${state.retryInMs / 1000} s…"
    is ModeAState.Stopped -> when (state.reason) {
        StopReason.UNREACHABLE -> "Gave up: the controller stayed out of reach for 5 minutes. Turn it on and connect again."
        StopReason.NO_ANSWER -> "It connected but never answered in Mode A. Is this a MOGA with its switch on A?"
        StopReason.BLUETOOTH_UNAVAILABLE -> "Bluetooth is off, or Swivel lost the Nearby devices permission."
    }
}
