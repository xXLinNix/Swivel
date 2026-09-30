package io.github.xxlinnix.swivel.core.bridge

import io.github.xxlinnix.swivel.core.hid.AndroidKeyCodes
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.model.GamepadButton

/** Which key codes a game expects, fixed by the registration call it used. */
enum class KeyCodeStyle {
    /** Games on SDKs before 1.3 call registerListener and getKeyCode. */
    LEGACY,

    /** SDK 1.3 and later call registerListener2 and getKeyCode2. */
    STANDARD,
}

/** The link as a game sees it. */
enum class SdkConnection { DISCONNECTED, CONNECTING, CONNECTED }

/** One key press or release for a game. */
data class SdkKeyChange(val button: GamepadButton, val action: Int)

/** Answers the SDK's questions and turns snapshot changes into SDK events. */
object MogaSdkMapping {
    private val standardCodes = mapOf(
        GamepadButton.A to AndroidKeyCodes.BUTTON_A,
        GamepadButton.B to AndroidKeyCodes.BUTTON_B,
        GamepadButton.X to AndroidKeyCodes.BUTTON_X,
        GamepadButton.Y to AndroidKeyCodes.BUTTON_Y,
        GamepadButton.L1 to AndroidKeyCodes.BUTTON_L1,
        GamepadButton.R1 to AndroidKeyCodes.BUTTON_R1,
        GamepadButton.L2 to AndroidKeyCodes.BUTTON_L2,
        GamepadButton.R2 to AndroidKeyCodes.BUTTON_R2,
        GamepadButton.L3 to AndroidKeyCodes.BUTTON_THUMBL,
        GamepadButton.R3 to AndroidKeyCodes.BUTTON_THUMBR,
        GamepadButton.START to AndroidKeyCodes.BUTTON_START,
        GamepadButton.SELECT to AndroidKeyCodes.BUTTON_SELECT,
        GamepadButton.DPAD_UP to AndroidKeyCodes.DPAD_UP,
        GamepadButton.DPAD_DOWN to AndroidKeyCodes.DPAD_DOWN,
        GamepadButton.DPAD_LEFT to AndroidKeyCodes.DPAD_LEFT,
        GamepadButton.DPAD_RIGHT to AndroidKeyCodes.DPAD_RIGHT,
    )

    fun keyCode(button: GamepadButton, style: KeyCodeStyle): Int = when {
        style == KeyCodeStyle.LEGACY && button == GamepadButton.X -> MogaSdk.LEGACY_KEYCODE_BUTTON_X
        style == KeyCodeStyle.LEGACY && button == GamepadButton.Y -> MogaSdk.LEGACY_KEYCODE_BUTTON_Y
        else -> standardCodes.getValue(button)
    }

    fun buttonFor(keyCode: Int, style: KeyCodeStyle): GamepadButton? =
        GamepadButton.entries.firstOrNull { keyCode(it, style) == keyCode }

    /** getKeyCode answers with an action: down or up. Unknown keys are up. */
    fun keyAction(snapshot: ControllerSnapshot, keyCode: Int, style: KeyCodeStyle): Int {
        val button = buttonFor(keyCode, style) ?: return MogaSdk.KEY_ACTION_UP
        return if (button in snapshot.pressed) MogaSdk.KEY_ACTION_DOWN else MogaSdk.KEY_ACTION_UP
    }

    /** Snapshot axes already use the SDK's ids and signs (Y up is -1, as Pivot sent it). */
    fun axisValue(snapshot: ControllerSnapshot, axis: Int): Float = snapshot.rawAxes[axis] ?: 0f

    fun motionAxes(snapshot: ControllerSnapshot): Map<Int, Float> =
        MogaSdk.MOTION_AXES.associateWith { axisValue(snapshot, it) }

    fun keyChanges(before: ControllerSnapshot, after: ControllerSnapshot): List<SdkKeyChange> {
        val released = (before.pressed - after.pressed).map { SdkKeyChange(it, MogaSdk.KEY_ACTION_UP) }
        val pressed = (after.pressed - before.pressed).map { SdkKeyChange(it, MogaSdk.KEY_ACTION_DOWN) }
        return (released + pressed).sortedBy { it.button.ordinal }
    }

    fun axesChanged(before: ControllerSnapshot, after: ControllerSnapshot): Boolean =
        motionAxes(before) != motionAxes(after)

    /**
     * getState. [firstGeneration] picks MOGA or MOGA Pro as the version. A controller id
     * other than 1 is a controller that does not exist.
     */
    fun state(
        controllerId: Int,
        state: Int,
        connection: SdkConnection,
        lowBattery: Boolean,
        firstGeneration: Boolean,
    ): Int {
        if (controllerId != MogaSdk.CONTROLLER_ID) {
            return if (state == MogaSdk.STATE_CONNECTION) MogaSdk.CONNECTION_DISCONNECTED else MogaSdk.FALSE
        }
        return when (state) {
            MogaSdk.STATE_CONNECTION -> connectionValue(connection)
            MogaSdk.STATE_POWER_LOW -> if (lowBattery) MogaSdk.TRUE else MogaSdk.FALSE
            MogaSdk.STATE_SUPPORTED_VERSION, MogaSdk.STATE_SELECTED_VERSION ->
                if (firstGeneration) MogaSdk.VERSION_MOGA else MogaSdk.VERSION_MOGA_PRO
            else -> MogaSdk.FALSE
        }
    }

    fun connectionValue(connection: SdkConnection): Int = when (connection) {
        SdkConnection.DISCONNECTED -> MogaSdk.CONNECTION_DISCONNECTED
        SdkConnection.CONNECTING -> MogaSdk.CONNECTION_CONNECTING
        SdkConnection.CONNECTED -> MogaSdk.CONNECTION_CONNECTED
    }

    /** A paused, stopped or destroyed game gets no input, as with Pivot. */
    fun receivesEvents(activityEvent: Int): Boolean =
        activityEvent != MogaSdk.ACTIVITY_PAUSE &&
            activityEvent != MogaSdk.ACTIVITY_STOP &&
            activityEvent != MogaSdk.ACTIVITY_DESTROY
}
