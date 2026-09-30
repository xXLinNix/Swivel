package io.github.xxlinnix.swivel.core.bridge

import io.github.xxlinnix.swivel.core.hid.AndroidAxes
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.model.GamepadButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MogaSdkMappingTest {
    private fun pressing(vararg buttons: GamepadButton) = ControllerSnapshot(keyButtons = buttons.toSet())

    @Test
    fun standardCodesAreAndroidsAndMatchTheSdkConstants() {
        // The values below are the constants in SDK 1.3.0's com.bda.controller.KeyEvent.
        assertEquals(96, MogaSdkMapping.keyCode(GamepadButton.A, KeyCodeStyle.STANDARD))
        assertEquals(97, MogaSdkMapping.keyCode(GamepadButton.B, KeyCodeStyle.STANDARD))
        assertEquals(99, MogaSdkMapping.keyCode(GamepadButton.X, KeyCodeStyle.STANDARD))
        assertEquals(100, MogaSdkMapping.keyCode(GamepadButton.Y, KeyCodeStyle.STANDARD))
        assertEquals(108, MogaSdkMapping.keyCode(GamepadButton.START, KeyCodeStyle.STANDARD))
        assertEquals(109, MogaSdkMapping.keyCode(GamepadButton.SELECT, KeyCodeStyle.STANDARD))
        assertEquals(19, MogaSdkMapping.keyCode(GamepadButton.DPAD_UP, KeyCodeStyle.STANDARD))
    }

    @Test
    fun legacyGamesGetTheOldXAndYCodes() {
        assertEquals(98, MogaSdkMapping.keyCode(GamepadButton.X, KeyCodeStyle.LEGACY))
        assertEquals(99, MogaSdkMapping.keyCode(GamepadButton.Y, KeyCodeStyle.LEGACY))
        assertEquals(96, MogaSdkMapping.keyCode(GamepadButton.A, KeyCodeStyle.LEGACY))
        // 99 means Y to a legacy game and X to a standard one.
        assertEquals(GamepadButton.Y, MogaSdkMapping.buttonFor(99, KeyCodeStyle.LEGACY))
        assertEquals(GamepadButton.X, MogaSdkMapping.buttonFor(99, KeyCodeStyle.STANDARD))
        assertNull(MogaSdkMapping.buttonFor(100, KeyCodeStyle.LEGACY))
    }

    @Test
    fun everyButtonHasADistinctCodeInEachStyle() {
        for (style in KeyCodeStyle.entries) {
            val codes = GamepadButton.entries.map { MogaSdkMapping.keyCode(it, style) }
            assertEquals(codes.size, codes.toSet().size, "$style")
        }
    }

    @Test
    fun getKeyCodeAnswersDownOrUp() {
        val snapshot = pressing(GamepadButton.A)
        assertEquals(MogaSdk.KEY_ACTION_DOWN, MogaSdkMapping.keyAction(snapshot, 96, KeyCodeStyle.STANDARD))
        assertEquals(MogaSdk.KEY_ACTION_UP, MogaSdkMapping.keyAction(snapshot, 97, KeyCodeStyle.STANDARD))
        assertEquals(MogaSdk.KEY_ACTION_UP, MogaSdkMapping.keyAction(snapshot, 12345, KeyCodeStyle.STANDARD))
    }

    @Test
    fun keyChangesListReleasesAndPresses() {
        val changes = MogaSdkMapping.keyChanges(pressing(GamepadButton.A, GamepadButton.B), pressing(GamepadButton.B, GamepadButton.X))
        assertEquals(
            listOf(SdkKeyChange(GamepadButton.A, MogaSdk.KEY_ACTION_UP), SdkKeyChange(GamepadButton.X, MogaSdk.KEY_ACTION_DOWN)),
            changes,
        )
        assertTrue(MogaSdkMapping.keyChanges(pressing(GamepadButton.A), pressing(GamepadButton.A)).isEmpty())
    }

    @Test
    fun motionCarriesTheSixAxesInTheSdksOrder() {
        val snapshot = ControllerSnapshot(rawAxes = mapOf(AndroidAxes.X to 0.5f, AndroidAxes.Y to -1f, AndroidAxes.RZ to 0.25f))
        assertEquals(
            mapOf(0 to 0.5f, 1 to -1f, 11 to 0f, 14 to 0.25f, 17 to 0f, 18 to 0f),
            MogaSdkMapping.motionAxes(snapshot),
        )
        assertTrue(MogaSdkMapping.axesChanged(ControllerSnapshot(), snapshot))
        assertFalse(MogaSdkMapping.axesChanged(snapshot, snapshot.copy(keyButtons = setOf(GamepadButton.A))))
    }

    @Test
    fun stateAnswersForControllerOne() {
        fun state(s: Int, connection: SdkConnection = SdkConnection.CONNECTED, low: Boolean = false, first: Boolean = true) =
            MogaSdkMapping.state(1, s, connection, low, first)
        assertEquals(MogaSdk.CONNECTION_CONNECTED, state(MogaSdk.STATE_CONNECTION))
        assertEquals(MogaSdk.CONNECTION_CONNECTING, state(MogaSdk.STATE_CONNECTION, SdkConnection.CONNECTING))
        assertEquals(MogaSdk.CONNECTION_DISCONNECTED, state(MogaSdk.STATE_CONNECTION, SdkConnection.DISCONNECTED))
        assertEquals(MogaSdk.TRUE, state(MogaSdk.STATE_POWER_LOW, low = true))
        assertEquals(MogaSdk.FALSE, state(MogaSdk.STATE_POWER_LOW))
        assertEquals(MogaSdk.VERSION_MOGA, state(MogaSdk.STATE_SELECTED_VERSION))
        assertEquals(MogaSdk.VERSION_MOGA_PRO, state(MogaSdk.STATE_SUPPORTED_VERSION, first = false))
        assertEquals(MogaSdk.FALSE, state(99))
    }

    @Test
    fun otherControllerIdsDoNotExist() {
        assertEquals(
            MogaSdk.CONNECTION_DISCONNECTED,
            MogaSdkMapping.state(2, MogaSdk.STATE_CONNECTION, SdkConnection.CONNECTED, false, true),
        )
    }

    @Test
    fun pausedGamesGetNoInput() {
        assertTrue(MogaSdkMapping.receivesEvents(MogaSdk.ACTIVITY_RESUME))
        assertTrue(MogaSdkMapping.receivesEvents(MogaSdk.ACTIVITY_SERVICE_CONNECTED))
        assertTrue(MogaSdkMapping.receivesEvents(0)) // A game that never reports its lifecycle.
        assertFalse(MogaSdkMapping.receivesEvents(MogaSdk.ACTIVITY_PAUSE))
        assertFalse(MogaSdkMapping.receivesEvents(MogaSdk.ACTIVITY_STOP))
        assertFalse(MogaSdkMapping.receivesEvents(MogaSdk.ACTIVITY_DESTROY))
    }
}
