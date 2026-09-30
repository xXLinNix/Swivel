package io.github.xxlinnix.swivel.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.data.input.GamepadInfo
import io.github.xxlinnix.swivel.data.input.KeyLogEntry
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

data class TestUiState(
    val loading: Boolean = true,
    /** Null while the controller is disconnected, for example asleep. */
    val info: GamepadInfo? = null,
    val snapshot: ControllerSnapshot = ControllerSnapshot(),
    val keyLog: List<KeyLogEntry> = emptyList(),
    val battery: BatteryReading = BatteryReading.NotReported,
)

/**
 * Live input from one gamepad. The gamepad is identified by its input-device descriptor,
 * which stays the same when the controller sleeps and reconnects under a new device id.
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
                flowOf(TestUiState(loading = false))
            } else {
                combine(
                    repository.snapshot(info.deviceId),
                    repository.keyLog.map { log -> log.filter { it.deviceId == info.deviceId } },
                    batteryOf(info.deviceId),
                ) { snapshot, log, battery ->
                    TestUiState(loading = false, info = info, snapshot = snapshot, keyLog = log, battery = battery)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TestUiState())

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
