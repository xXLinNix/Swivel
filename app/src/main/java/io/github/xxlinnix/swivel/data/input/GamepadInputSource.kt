package io.github.xxlinnix.swivel.data.input

import android.content.Context
import android.hardware.BatteryState
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.github.xxlinnix.swivel.core.detect.MogaNames
import io.github.xxlinnix.swivel.core.hid.HidSnapshotReducer
import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Gamepads connected over Bluetooth HID (Mode B), and their live input.
 *
 * Android only delivers controller events to the focused window, so MainActivity passes
 * every key and motion event through [onKeyEvent] and [onMotionEvent]. This is the
 * pattern from developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input.
 * The instance lives as long as the app process.
 */
class GamepadInputSource(context: Context) : InputManager.InputDeviceListener {
    private val inputManager = context.getSystemService(InputManager::class.java)

    private val _gamepads = MutableStateFlow(scanGamepads())
    val gamepads: StateFlow<List<GamepadInfo>> = _gamepads.asStateFlow()

    private val snapshots = MutableStateFlow<Map<Int, ControllerSnapshot>>(emptyMap())

    private val _keyLog = MutableStateFlow<List<KeyLogEntry>>(emptyList())
    val keyLog: StateFlow<List<KeyLogEntry>> = _keyLog.asStateFlow()

    /**
     * While true, gamepad events are consumed instead of reaching the UI. The test screen
     * sets it so that pressing B does not act as Back and the D-pad does not move focus.
     */
    @Volatile
    var captureAll: Boolean = false

    init {
        inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
    }

    fun snapshot(deviceId: Int): Flow<ControllerSnapshot> =
        snapshots.map { it[deviceId] ?: ControllerSnapshot() }.distinctUntilChanged()

    /** Returns true when the event came from a gamepad and was recorded. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        if (!device.isGamepad()) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true
        if (event.repeatCount > 0) return true

        val down = event.action == KeyEvent.ACTION_DOWN
        snapshots.update { all ->
            val current = all[event.deviceId] ?: ControllerSnapshot()
            all + (event.deviceId to HidSnapshotReducer.onKey(current, event.keyCode, down))
        }
        val entry = KeyLogEntry(
            uptimeMillis = event.eventTime,
            deviceId = event.deviceId,
            keyCode = event.keyCode,
            label = KeyEvent.keyCodeToString(event.keyCode),
            scanCode = event.scanCode,
            down = down,
        )
        _keyLog.update { (listOf(entry) + it).take(KEY_LOG_SIZE) }
        return true
    }

    /** Returns true when the event was joystick motion from a gamepad and was recorded. */
    fun onMotionEvent(event: MotionEvent): Boolean {
        val device = event.device ?: return false
        if (!device.isGamepad()) return false
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return false

        val axes = joystickAxes(device).associateWith { event.getAxisValue(it) }
        snapshots.update { all ->
            val current = all[event.deviceId] ?: ControllerSnapshot()
            all + (event.deviceId to HidSnapshotReducer.onMotion(current, axes))
        }
        return true
    }

    /**
     * The battery level Android reads from the controller's HID reports (Android 12 and
     * later). Many controllers do not report one this way.
     */
    fun battery(deviceId: Int): BatteryReading {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return BatteryReading.NotReported
        val state = InputDevice.getDevice(deviceId)?.batteryState ?: return BatteryReading.NotReported
        if (!state.isPresent || state.capacity.isNaN()) return BatteryReading.NotReported
        val charging = when (state.status) {
            BatteryState.STATUS_CHARGING -> true
            BatteryState.STATUS_DISCHARGING, BatteryState.STATUS_NOT_CHARGING, BatteryState.STATUS_FULL -> false
            else -> null
        }
        return BatteryReading.Level(state.capacity, charging)
    }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()

    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    override fun onInputDeviceRemoved(deviceId: Int) {
        snapshots.update { it - deviceId }
        refresh()
    }

    private fun refresh() {
        _gamepads.value = scanGamepads()
    }

    private fun scanGamepads(): List<GamepadInfo> =
        InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isGamepad() }
            .map { it.toInfo() }

    private fun InputDevice.isGamepad(): Boolean =
        !isVirtual && (supportsSource(InputDevice.SOURCE_GAMEPAD) || supportsSource(InputDevice.SOURCE_JOYSTICK))

    private fun joystickAxes(device: InputDevice): List<Int> =
        device.motionRanges.filter { it.isFromSource(InputDevice.SOURCE_JOYSTICK) }.map { it.axis }.distinct()

    private fun InputDevice.toInfo(): GamepadInfo = GamepadInfo(
        deviceId = id,
        name = name,
        vendorId = vendorId,
        productId = productId,
        descriptor = descriptor,
        controllerNumber = controllerNumber,
        axes = motionRanges
            .filter { it.isFromSource(InputDevice.SOURCE_JOYSTICK) }
            .distinctBy { it.axis }
            .map { AxisInfo(it.axis, MotionEvent.axisToString(it.axis), it.min, it.max, it.flat, it.fuzz) },
        looksLikeMoga = MogaNames.isMoga(name) || vendorId == POWERA_VENDOR_ID,
    )

    private companion object {
        const val KEY_LOG_SIZE = 30

        /**
         * The USB vendor id PowerA products report. Unconfirmed for MOGA controllers over
         * Bluetooth, so it only adds to the name check (docs/RESEARCH.md).
         */
        const val POWERA_VENDOR_ID = 0x20D6
    }
}
