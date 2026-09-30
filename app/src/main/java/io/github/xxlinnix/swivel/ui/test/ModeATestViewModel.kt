package io.github.xxlinnix.swivel.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xxlinnix.swivel.core.hid.AndroidAxes
import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.model.GamepadButton
import io.github.xxlinnix.swivel.core.protocol.ModeAGeneration
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.data.modea.ModeAState
import io.github.xxlinnix.swivel.data.modea.address
import io.github.xxlinnix.swivel.ui.common.buttonLabel
import io.github.xxlinnix.swivel.ui.common.modeAStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Live input from the Mode A link, laid out like the Mode B test screen. */
class ModeATestViewModel(
    val address: String,
    private val repository: ControllerRepository,
) : ViewModel() {
    /** Mode A has no key events, so presses and releases come from comparing reports. */
    private val events = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            var previous = emptySet<GamepadButton>()
            repository.modeASnapshot.collect { snapshot ->
                val now = snapshot.pressed
                val changes = (now - previous).map { "down ${buttonLabel(it)}" } + (previous - now).map { "up   ${buttonLabel(it)}" }
                if (changes.isNotEmpty()) events.update { (changes + it).take(EVENT_LOG_SIZE) }
                previous = now
            }
        }
    }

    val state: StateFlow<TestUiState> = combine(
        repository.modeAState,
        repository.modeASnapshot,
        repository.modeABattery,
        repository.modeALastReport,
        events,
    ) { link, snapshot, battery, report, log -> toUi(link, snapshot, battery, report, log) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TestUiState())

    /** Only from the visible screen: it starts a foreground service. */
    fun reconnect() = repository.connectModeA(address)

    /**
     * With the virtual gamepad on, the controller's presses also reach Swivel itself.
     * Swallow them here, as the Mode B test screen does, so B and Select do not act as Back.
     */
    fun setCapture(capture: Boolean) = repository.setCaptureAll(capture)

    override fun onCleared() {
        repository.setCaptureAll(false)
    }

    private fun toUi(
        link: ModeAState,
        snapshot: ControllerSnapshot,
        battery: BatteryReading,
        report: String?,
        log: List<String>,
    ): TestUiState {
        if (link.address != address) {
            return TestUiState(loading = false, status = modeAStatus(ModeAState.Idle), canReconnect = true)
        }
        if (link !is ModeAState.Connected) {
            return TestUiState(
                loading = link is ModeAState.Connecting,
                status = modeAStatus(link),
                canReconnect = link is ModeAState.Stopped,
            )
        }
        val first = link.generation == ModeAGeneration.FIRST
        return TestUiState(
            loading = false,
            connected = true,
            title = link.name ?: address,
            summary = "MOGA in Mode A",
            details = listOf(
                address,
                if (first) "first-generation reports, 12 bytes" else "second-generation reports, 14 bytes",
            ),
            snapshot = snapshot,
            axes = AXES.map { (axis, label) -> AxisRow(label, snapshot.rawAxes[axis] ?: 0f, "") },
            events = log,
            battery = battery,
            rawReport = report,
            hint = if (first) "A MOGA Pocket has no D-pad, L2/R2 or stick clicks, so those lamps stay dark." else null,
        )
    }

    private companion object {
        const val EVENT_LOG_SIZE = 30

        val AXES = listOf(
            AndroidAxes.X to "Left X (byte 6)",
            AndroidAxes.Y to "Left Y (byte 7, flipped)",
            AndroidAxes.Z to "Right X (byte 8)",
            AndroidAxes.RZ to "Right Y (byte 9, flipped)",
            AndroidAxes.LTRIGGER to "L2",
            AndroidAxes.RTRIGGER to "R2",
        )
    }
}
