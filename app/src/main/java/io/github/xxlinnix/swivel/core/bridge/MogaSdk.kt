package io.github.xxlinnix.swivel.core.bridge

/**
 * The Binder contract between games built with PowerA's MOGA SDK (`com.bda.controller`)
 * and the service the Pivot app ran. The numbers and strings come from SDK 1.3.0 and the
 * Pivot 1.23 APK (docs/RESEARCH.md, "The old SDK bridge"). Only these interoperability
 * facts are used; no PowerA code.
 */
object MogaSdk {
    /** The implicit intent action the SDK binds with. */
    const val SERVICE_ACTION = "com.bda.controller.IControllerService"
    const val SERVICE_DESCRIPTOR = "com.bda.controller.IControllerService"
    const val LISTENER_DESCRIPTOR = "com.bda.controller.IControllerListener"

    // IControllerService transactions.
    const val TX_REGISTER_LISTENER = 1
    const val TX_UNREGISTER_LISTENER = 2
    const val TX_REGISTER_MONITOR = 3
    const val TX_UNREGISTER_MONITOR = 4
    const val TX_GET_INFO = 5
    const val TX_GET_KEY_CODE = 6
    const val TX_GET_AXIS_VALUE = 7
    const val TX_GET_STATE = 8
    const val TX_SEND_MESSAGE = 9
    const val TX_REGISTER_LISTENER_2 = 10
    const val TX_GET_KEY_CODE_2 = 11
    const val TX_ALLOW_NEW_CONNECTIONS = 12
    const val TX_DISALLOW_NEW_CONNECTIONS = 13
    const val TX_IS_ALLOWING_NEW_CONNECTIONS = 14

    // IControllerListener transactions.
    const val TX_ON_KEY_EVENT = 1
    const val TX_ON_MOTION_EVENT = 2
    const val TX_ON_STATE_EVENT = 3

    /** The SDK only ever asks about controller 1. */
    const val CONTROLLER_ID = 1

    const val KEY_ACTION_DOWN = 0
    const val KEY_ACTION_UP = 1

    const val INFO_KNOWN_DEVICE_COUNT = 1
    const val INFO_ACTIVE_DEVICE_COUNT = 2

    const val STATE_CONNECTION = 1
    const val STATE_POWER_LOW = 2
    const val STATE_SUPPORTED_VERSION = 3
    const val STATE_SELECTED_VERSION = 4

    const val CONNECTION_DISCONNECTED = 0
    const val CONNECTION_CONNECTED = 1
    const val CONNECTION_CONNECTING = 2

    const val FALSE = 0
    const val TRUE = 1

    /** The first-generation MOGA (the Pocket) and the MOGA Pro family. */
    const val VERSION_MOGA = 0
    const val VERSION_MOGA_PRO = 1

    /** sendMessage's only message: the game's activity moved through its lifecycle. */
    const val MSG_SET_ACTIVITY_EVENT = 1

    // Activity events the SDK reports.
    const val ACTIVITY_CREATE = 1
    const val ACTIVITY_DESTROY = 2
    const val ACTIVITY_START = 3
    const val ACTIVITY_STOP = 4
    const val ACTIVITY_RESUME = 5
    const val ACTIVITY_PAUSE = 6
    const val ACTIVITY_SERVICE_CONNECTED = 7

    /** Before SDK 1.3, X and Y used these codes (1.3 uses Android's 99 and 100). */
    const val LEGACY_KEYCODE_BUTTON_X = 98
    const val LEGACY_KEYCODE_BUTTON_Y = 99

    /** Axes in every motion event, as Pivot sent them: X, Y, Z, RZ, LTRIGGER, RTRIGGER. */
    val MOTION_AXES = listOf(0, 1, 11, 14, 17, 18)

    /** Pivot reported a precision of 127 for X and Y. */
    val MOTION_PRECISION = mapOf(0 to 127f, 1 to 127f)
}
