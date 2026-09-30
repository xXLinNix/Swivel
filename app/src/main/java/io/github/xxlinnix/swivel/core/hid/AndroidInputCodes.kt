package io.github.xxlinnix.swivel.core.hid

/**
 * Android's key codes and motion axis ids, copied as plain numbers so the mapping can be
 * tested on the JVM. They are part of Android's stable API and never change.
 * `AndroidInputCodesTest` checks every value against the framework's own constants.
 */
object AndroidKeyCodes {
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_L2 = 104
    const val BUTTON_R2 = 105
    const val BUTTON_THUMBL = 106
    const val BUTTON_THUMBR = 107
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109
}

object AndroidAxes {
    const val X = 0
    const val Y = 1
    const val Z = 11
    const val RX = 12
    const val RY = 13
    const val RZ = 14
    const val HAT_X = 15
    const val HAT_Y = 16
    const val LTRIGGER = 17
    const val RTRIGGER = 18
    const val GAS = 22
    const val BRAKE = 23
}
