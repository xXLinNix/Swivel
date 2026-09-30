package io.github.xxlinnix.swivel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.data.PairedMoga
import io.github.xxlinnix.swivel.data.input.GamepadInfo
import io.github.xxlinnix.swivel.data.modea.ModeAState
import io.github.xxlinnix.swivel.data.virtualpad.ShizukuStatus
import io.github.xxlinnix.swivel.data.virtualpad.VirtualPadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class HomeUiState(
    val gamepads: List<GamepadInfo> = emptyList(),
    /** Null when the app lacks the Nearby devices permission and cannot list pairings. */
    val paired: List<PairedMoga>? = null,
    val bluetoothOn: Boolean = true,
    val modeA: ModeAState = ModeAState.Idle,
    /** Old MOGA-enhanced games listening through the SDK bridge. */
    val bridgeGames: Int = 0,
    val shizuku: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val virtualPad: VirtualPadState = VirtualPadState.Off,
    val virtualPadEnabled: Boolean = true,
)

class HomeViewModel(private val repository: ControllerRepository) : ViewModel() {
    private val resumes = MutableStateFlow(0)

    val state: StateFlow<HomeUiState> = combine(
        repository.gamepads,
        resumes,
        repository.bluetoothEvents().map { }.onStart { emit(Unit) },
        repository.modeAState,
        repository.bridgeGames,
    ) { gamepads, _, _, modeA, bridgeGames ->
        HomeUiState(
            gamepads = gamepads,
            paired = if (repository.hasConnectPermission()) repository.pairedMogas() else null,
            bluetoothOn = repository.bluetoothEnabled,
            modeA = modeA,
            bridgeGames = bridgeGames,
        )
    }.combine(
        combine(repository.shizukuStatus, repository.virtualPadState, repository.virtualPadEnabled) { shizuku, pad, enabled ->
            Triple(shizuku, pad, enabled)
        },
    ) { home, (shizuku, pad, enabled) ->
        home.copy(shizuku = shizuku, virtualPad = pad, virtualPadEnabled = enabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Pairings can change in Android's settings while the app is in the background. */
    fun onResume() {
        resumes.update { it + 1 }
        repository.refreshShizuku() // Permission may have changed in the Shizuku app.
    }

    fun setVirtualPadEnabled(enabled: Boolean) = repository.setVirtualPadEnabled(enabled)

    fun allowInShizuku() = repository.requestShizukuPermission()

    /** Only from the visible screen: it starts a foreground service. */
    fun connectModeA(address: String) = repository.connectModeA(address)

    fun disconnectModeA() = repository.disconnectModeA()
}
