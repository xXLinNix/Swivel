package io.github.xxlinnix.swivel.core.hid

import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.model.GamepadButton
import io.github.xxlinnix.swivel.core.model.Stick

/**
 * Folds Android gamepad key and motion events into a [ControllerSnapshot].
 *
 * The mapping follows Android's standard gamepad layout
 * (developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input):
 * left stick on X/Y, right stick on Z/RZ (or RX/RY when a controller has no Z/RZ),
 * triggers on LTRIGGER/RTRIGGER or BRAKE/GAS, and a D-pad reported either as keys or
 * as the HAT axes. MOGA controllers in Mode B report their triggers on BRAKE and GAS.
 */
object HidSnapshotReducer {
    private val buttonsByKeyCode = mapOf(
        AndroidKeyCodes.BUTTON_A to GamepadButton.A,
        AndroidKeyCodes.BUTTON_B to GamepadButton.B,
        AndroidKeyCodes.BUTTON_X to GamepadButton.X,
        AndroidKeyCodes.BUTTON_Y to GamepadButton.Y,
        AndroidKeyCodes.BUTTON_L1 to GamepadButton.L1,
        AndroidKeyCodes.BUTTON_R1 to GamepadButton.R1,
        AndroidKeyCodes.BUTTON_L2 to GamepadButton.L2,
        AndroidKeyCodes.BUTTON_R2 to GamepadButton.R2,
        AndroidKeyCodes.BUTTON_THUMBL to GamepadButton.L3,
        AndroidKeyCodes.BUTTON_THUMBR to GamepadButton.R3,
        AndroidKeyCodes.BUTTON_START to GamepadButton.START,
        AndroidKeyCodes.BUTTON_SELECT to GamepadButton.SELECT,
        AndroidKeyCodes.DPAD_UP to GamepadButton.DPAD_UP,
        AndroidKeyCodes.DPAD_DOWN to GamepadButton.DPAD_DOWN,
        AndroidKeyCodes.DPAD_LEFT to GamepadButton.DPAD_LEFT,
        AndroidKeyCodes.DPAD_RIGHT to GamepadButton.DPAD_RIGHT,
    )

    /** How far a HAT axis has to move before it counts as a D-pad press. */
    private const val HAT_THRESHOLD = 0.5f

    fun buttonFor(keyCode: Int): GamepadButton? = buttonsByKeyCode[keyCode]

    fun onKey(state: ControllerSnapshot, keyCode: Int, down: Boolean): ControllerSnapshot {
        val button = buttonFor(keyCode)
        return if (button != null) {
            state.copy(keyButtons = if (down) state.keyButtons + button else state.keyButtons - button)
        } else {
            val keys = if (down) state.unmappedKeysDown + keyCode else state.unmappedKeysDown - keyCode
            state.copy(unmappedKeysDown = keys)
        }
    }

    /**
     * Applies one motion event. [axes] holds every axis the device declares, so an axis
     * missing from the map is one the controller does not have.
     */
    fun onMotion(state: ControllerSnapshot, axes: Map<Int, Float>): ControllerSnapshot {
        fun axis(id: Int) = axes[id] ?: 0f

        val rightStick = if (AndroidAxes.Z in axes && AndroidAxes.RZ in axes) {
            Stick(axis(AndroidAxes.Z), axis(AndroidAxes.RZ))
        } else {
            Stick(axis(AndroidAxes.RX), axis(AndroidAxes.RY))
        }

        val hatX = axis(AndroidAxes.HAT_X)
        val hatY = axis(AndroidAxes.HAT_Y)
        val hatButtons = buildSet {
            if (hatX <= -HAT_THRESHOLD) add(GamepadButton.DPAD_LEFT)
            if (hatX >= HAT_THRESHOLD) add(GamepadButton.DPAD_RIGHT)
            if (hatY <= -HAT_THRESHOLD) add(GamepadButton.DPAD_UP)
            if (hatY >= HAT_THRESHOLD) add(GamepadButton.DPAD_DOWN)
        }

        return state.copy(
            hatButtons = hatButtons,
            leftStick = Stick(axis(AndroidAxes.X), axis(AndroidAxes.Y)),
            rightStick = rightStick,
            leftTrigger = maxOf(axis(AndroidAxes.LTRIGGER), axis(AndroidAxes.BRAKE)),
            rightTrigger = maxOf(axis(AndroidAxes.RTRIGGER), axis(AndroidAxes.GAS)),
            rawAxes = axes,
        )
    }
}
