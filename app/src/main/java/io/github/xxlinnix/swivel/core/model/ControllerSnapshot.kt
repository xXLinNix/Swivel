package io.github.xxlinnix.swivel.core.model

/** The physical controls on a MOGA controller, named after the labels printed on it. */
enum class GamepadButton {
    A, B, X, Y,
    L1, R1, L2, R2,
    L3, R3,
    START, SELECT,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
}

/** A stick position, each axis from -1 (left, up) to 1 (right, down), as Android reports it. */
data class Stick(val x: Float = 0f, val y: Float = 0f)

/**
 * Everything the test screen shows about one controller at one moment.
 *
 * The named fields are the interpreted view. [rawAxes] and [unmappedKeysDown] are the
 * uninterpreted values, so a control that the mapping gets wrong is still visible.
 */
data class ControllerSnapshot(
    /** Buttons held down according to key events. */
    val keyButtons: Set<GamepadButton> = emptySet(),
    /** D-pad directions held according to the HAT axes, for controllers that report the D-pad that way. */
    val hatButtons: Set<GamepadButton> = emptySet(),
    val leftStick: Stick = Stick(),
    val rightStick: Stick = Stick(),
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    /** Every axis the last motion event carried, keyed by Android axis id. */
    val rawAxes: Map<Int, Float> = emptyMap(),
    /** Key codes held down that do not map to a [GamepadButton]. */
    val unmappedKeysDown: Set<Int> = emptySet(),
) {
    /** Every button held down, however the controller reported it. */
    val pressed: Set<GamepadButton> get() = keyButtons + hatButtons
}

/** A battery level as far as the connection reports one. */
sealed interface BatteryReading {
    /** The connection does not report a battery at all. */
    data object NotReported : BatteryReading

    /** A level from 0 to 1, when the controller reports one. */
    data class Level(val fraction: Float, val charging: Boolean?) : BatteryReading

    /** Only a low-battery flag is available (MOGA Mode A, milestone 2). */
    data class LowFlag(val low: Boolean) : BatteryReading
}
