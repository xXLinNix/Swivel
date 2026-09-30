package io.github.xxlinnix.swivel.core.hid

import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the copied numbers to the framework's constants, which the compiler inlines. */
class AndroidInputCodesTest {
    @Test
    fun keyCodesMatchTheFramework() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, AndroidKeyCodes.DPAD_UP)
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyCodes.DPAD_DOWN)
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyCodes.DPAD_LEFT)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyCodes.DPAD_RIGHT)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, AndroidKeyCodes.BUTTON_A)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, AndroidKeyCodes.BUTTON_B)
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, AndroidKeyCodes.BUTTON_X)
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, AndroidKeyCodes.BUTTON_Y)
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, AndroidKeyCodes.BUTTON_L1)
        assertEquals(KeyEvent.KEYCODE_BUTTON_R1, AndroidKeyCodes.BUTTON_R1)
        assertEquals(KeyEvent.KEYCODE_BUTTON_L2, AndroidKeyCodes.BUTTON_L2)
        assertEquals(KeyEvent.KEYCODE_BUTTON_R2, AndroidKeyCodes.BUTTON_R2)
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBL, AndroidKeyCodes.BUTTON_THUMBL)
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBR, AndroidKeyCodes.BUTTON_THUMBR)
        assertEquals(KeyEvent.KEYCODE_BUTTON_START, AndroidKeyCodes.BUTTON_START)
        assertEquals(KeyEvent.KEYCODE_BUTTON_SELECT, AndroidKeyCodes.BUTTON_SELECT)
    }

    @Test
    fun axisIdsMatchTheFramework() {
        assertEquals(MotionEvent.AXIS_X, AndroidAxes.X)
        assertEquals(MotionEvent.AXIS_Y, AndroidAxes.Y)
        assertEquals(MotionEvent.AXIS_Z, AndroidAxes.Z)
        assertEquals(MotionEvent.AXIS_RX, AndroidAxes.RX)
        assertEquals(MotionEvent.AXIS_RY, AndroidAxes.RY)
        assertEquals(MotionEvent.AXIS_RZ, AndroidAxes.RZ)
        assertEquals(MotionEvent.AXIS_HAT_X, AndroidAxes.HAT_X)
        assertEquals(MotionEvent.AXIS_HAT_Y, AndroidAxes.HAT_Y)
        assertEquals(MotionEvent.AXIS_LTRIGGER, AndroidAxes.LTRIGGER)
        assertEquals(MotionEvent.AXIS_RTRIGGER, AndroidAxes.RTRIGGER)
        assertEquals(MotionEvent.AXIS_GAS, AndroidAxes.GAS)
        assertEquals(MotionEvent.AXIS_BRAKE, AndroidAxes.BRAKE)
    }
}
