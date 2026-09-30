package io.github.xxlinnix.swivel.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.data.input.GamepadInfo
import io.github.xxlinnix.swivel.data.input.KeyLogEntry
import io.github.xxlinnix.swivel.ui.common.hex4
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Live input from one Mode B gamepad. The gamepad is identified by its input-device
 * descriptor, which stays the same when the controller sleeps and reconnects under a new
 * device id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControllerTestViewModel(
    private val descriptor: String,
    private val repository: ControllerRepository,
) : ViewModel() {

    val state: StateFlow<TestUiState> = repository.gamepads
        .map { pads -> pads.firstOrNull { it.descriptor == descriptor } }
        .distinctUntilChanged()
        .flatMapLatest { info ->
            if (info == null) {
                flowOf(
                    TestUiState(
                        loading = false,
                        status = "The controller is disconnected. Press a button on it to wake it up and it will come back here.",
                    ),
                )
            } else {
                combine(
                    repository.snapshot(info.deviceId),
                    repository.keyLog.map { log -> log.filter { it.deviceId == info.deviceId } },
                    batteryOf(info.deviceId),
                ) { snapshot, log, battery -> connectedState(info, snapshot, log, battery) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TestUiState())

    private fun connectedState(
        info: GamepadInfo,
        snapshot: ControllerSnapshot,
        log: List<KeyLogEntry>,
        battery: BatteryReading,
    ) = TestUiState(
        loading = false,
        connected = true,
        title = info.name,
        summary = if (info.looksLikeMoga) "MOGA in Mode B (Bluetooth HID)" else "Bluetooth or USB gamepad",
        details = listOf("vendor ${hex4(info.vendorId)}  product ${hex4(info.productId)}  player ${info.controllerNumber}"),
        snapshot = snapshot,
        axes = info.axes.map { axis ->
            AxisRow(axis.label, snapshot.rawAxes[axis.axis] ?: 0f, "[${fmt(axis.min)}, ${fmt(axis.max)}] flat ${fmt(axis.flat)}")
        },
        events = log.map { "${if (it.down) "down" else "up  "} ${it.label} (${it.keyCode}) scan ${it.scanCode}" },
        battery = battery,
        hint = "Back and focus movement are off on this screen so every button can be tested. Use Back at the top.",
    )

    /** Android has no battery-changed callback for input devices, so the level is polled. */
    private fun batteryOf(deviceId: Int): Flow<BatteryReading> = flow {
        while (true) {
            emit(repository.battery(deviceId))
            delay(BATTERY_POLL_MS)
        }
    }.distinctUntilChanged()

    fun setCapture(capture: Boolean) {
        repository.setCaptureAll(capture)
    }

    override fun onCleared() {
        repository.setCaptureAll(false)
    }

    private companion object {
        const val BATTERY_POLL_MS = 5_000L
    }
}

internal fun fmt(value: Float): String = String.format(Locale.US, "%+.3f", value)
