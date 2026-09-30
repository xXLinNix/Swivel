package io.github.xxlinnix.swivel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.data.PairedMoga
import io.github.xxlinnix.swivel.data.input.GamepadInfo
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
)

class HomeViewModel(private val repository: ControllerRepository) : ViewModel() {
    private val resumes = MutableStateFlow(0)

    val state: StateFlow<HomeUiState> = combine(
        repository.gamepads,
        resumes,
        repository.bluetoothEvents().map { }.onStart { emit(Unit) },
    ) { gamepads, _, _ ->
        HomeUiState(
            gamepads = gamepads,
            paired = if (repository.hasConnectPermission()) repository.pairedMogas() else null,
            bluetoothOn = repository.bluetoothEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Pairings can change in Android's settings while the app is in the background. */
    fun onResume() {
        resumes.update { it + 1 }
    }
}
