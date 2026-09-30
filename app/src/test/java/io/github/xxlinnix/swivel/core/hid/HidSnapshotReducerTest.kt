package io.github.xxlinnix.swivel.core.hid

import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.model.GamepadButton
import io.github.xxlinnix.swivel.core.model.Stick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HidSnapshotReducerTest {
    private val empty = ControllerSnapshot()

    @Test
    fun keyDownAndUpTogglesTheButton() {
        val down = HidSnapshotReducer.onKey(empty, AndroidKeyCodes.BUTTON_A, down = true)
        assertEquals(setOf(GamepadButton.A), down.pressed)
        val up = HidSnapshotReducer.onKey(down, AndroidKeyCodes.BUTTON_A, down = false)
        assertTrue(up.pressed.isEmpty())
    }

    @Test
    fun everyMappedKeyCodeReachesADistinctButton() {
        val codes = listOf(
            AndroidKeyCodes.DPAD_UP, AndroidKeyCodes.DPAD_DOWN, AndroidKeyCodes.DPAD_LEFT, AndroidKeyCodes.DPAD_RIGHT,
            AndroidKeyCodes.BUTTON_A, AndroidKeyCodes.BUTTON_B, AndroidKeyCodes.BUTTON_X, AndroidKeyCodes.BUTTON_Y,
            AndroidKeyCodes.BUTTON_L1, AndroidKeyCodes.BUTTON_R1, AndroidKeyCodes.BUTTON_L2, AndroidKeyCodes.BUTTON_R2,
            AndroidKeyCodes.BUTTON_THUMBL, AndroidKeyCodes.BUTTON_THUMBR,
            AndroidKeyCodes.BUTTON_START, AndroidKeyCodes.BUTTON_SELECT,
        )
        val buttons = codes.map { HidSnapshotReducer.buttonFor(it) }
        assertEquals(GamepadButton.entries.toSet(), buttons.filterNotNull().toSet())
        assertEquals(codes.size, buttons.toSet().size)
    }

    @Test
    fun anUnmappedKeyIsKeptAsARawCode() {
        val down = HidSnapshotReducer.onKey(empty, 110, down = true)
        assertTrue(down.pressed.isEmpty())
        assertEquals(setOf(110), down.unmappedKeysDown)
        assertTrue(HidSnapshotReducer.onKey(down, 110, down = false).unmappedKeysDown.isEmpty())
    }

    @Test
    fun sticksUseXyAndZRzWhenPresent() {
        val axes = mapOf(
            AndroidAxes.X to 0.5f, AndroidAxes.Y to -1f,
            AndroidAxes.Z to -0.25f, AndroidAxes.RZ to 0.75f,
            AndroidAxes.RX to 0.9f, AndroidAxes.RY to 0.9f,
        )
        val state = HidSnapshotReducer.onMotion(empty, axes)
        assertEquals(Stick(0.5f, -1f), state.leftStick)
        assertEquals(Stick(-0.25f, 0.75f), state.rightStick)
        assertEquals(axes, state.rawAxes)
    }

    @Test
    fun rightStickFallsBackToRxRy() {
        val state = HidSnapshotReducer.onMotion(empty, mapOf(AndroidAxes.RX to 0.1f, AndroidAxes.RY to 0.2f))
        assertEquals(Stick(0.1f, 0.2f), state.rightStick)
    }

    @Test
    fun triggersReadBrakeAndGasAsWellAsTriggerAxes() {
        val mogaStyle = HidSnapshotReducer.onMotion(empty, mapOf(AndroidAxes.BRAKE to 0.4f, AndroidAxes.GAS to 1f))
        assertEquals(0.4f, mogaStyle.leftTrigger)
        assertEquals(1f, mogaStyle.rightTrigger)

        val standard = HidSnapshotReducer.onMotion(empty, mapOf(AndroidAxes.LTRIGGER to 0.6f, AndroidAxes.RTRIGGER to 0.2f))
        assertEquals(0.6f, standard.leftTrigger)
        assertEquals(0.2f, standard.rightTrigger)
    }

    @Test
    fun hatAxesPressAndReleaseTheDpad() {
        val upLeft = HidSnapshotReducer.onMotion(empty, mapOf(AndroidAxes.HAT_X to -1f, AndroidAxes.HAT_Y to -1f))
        assertEquals(setOf(GamepadButton.DPAD_LEFT, GamepadButton.DPAD_UP), upLeft.pressed)
        val released = HidSnapshotReducer.onMotion(upLeft, mapOf(AndroidAxes.HAT_X to 0f, AndroidAxes.HAT_Y to 0f))
        assertTrue(released.pressed.isEmpty())
    }

    @Test
    fun movingASticksKeepsADpadHeldAsAKey() {
        // Regression: HAT handling once cleared D-pad presses that arrived as key events.
        val held = HidSnapshotReducer.onKey(empty, AndroidKeyCodes.DPAD_UP, down = true)
        val moved = HidSnapshotReducer.onMotion(
            held,
            mapOf(AndroidAxes.X to 0.3f, AndroidAxes.HAT_X to 0f, AndroidAxes.HAT_Y to 0f),
        )
        assertEquals(setOf(GamepadButton.DPAD_UP), moved.pressed)
    }
}
