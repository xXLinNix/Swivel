package io.github.xxlinnix.swivel.data.input

/** A gamepad Android has an input device for, which for a MOGA means Mode B. */
data class GamepadInfo(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val descriptor: String,
    /** The player number Android assigned, or 0 for none. */
    val controllerNumber: Int,
    /** Every joystick axis the device declares, in the order Android lists them. */
    val axes: List<AxisInfo>,
    val looksLikeMoga: Boolean,
)

data class AxisInfo(
    val axis: Int,
    val label: String,
    val min: Float,
    val max: Float,
    val flat: Float,
    val fuzz: Float,
)

/** One key press or release, kept for the test screen's event log. */
data class KeyLogEntry(
    val uptimeMillis: Long,
    val deviceId: Int,
    val keyCode: Int,
    val label: String,
    val scanCode: Int,
    val down: Boolean,
)
