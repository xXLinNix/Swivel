package io.github.xxlinnix.swivel.ui.games

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xxlinnix.swivel.core.games.GameCatalog
import io.github.xxlinnix.swivel.core.games.GameFilter
import io.github.xxlinnix.swivel.core.games.InstalledGame
import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.data.modea.ModeAState
import io.github.xxlinnix.swivel.data.virtualpad.VirtualPadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether games will see a controller right now. */
enum class ControllerReadiness {
    /** Android has a gamepad: a Mode B controller, or the Pocket through the virtual gamepad. */
    READY,

    /** The Mode A link is up but the virtual gamepad is not (Shizuku stopped, or switched off). */
    LINKED_NOT_SHARED,

    NOT_CONNECTED,
}

data class GameRow(val game: InstalledGame, val knownMoga: Boolean)

data class GamesUiState(
    val loading: Boolean = true,
    val filter: GameFilter = GameFilter.CONTROLLER_SUPPORT,
    val games: List<GameRow> = emptyList(),
    val readiness: ControllerReadiness = ControllerReadiness.NOT_CONNECTED,
    /** A paired Mode A controller to offer a Connect button for, if any. */
    val connectAddress: String? = null,
    val launchFailed: String? = null,
)

class GamesViewModel(private val repository: ControllerRepository) : ViewModel() {
    private val apps = MutableStateFlow<List<InstalledGame>?>(null)
    private val filter = MutableStateFlow(GameFilter.CONTROLLER_SUPPORT)
    private val connectAddress = MutableStateFlow<String?>(null)
    private val launchFailed = MutableStateFlow<String?>(null)

    val state: StateFlow<GamesUiState> = combine(
        combine(apps, filter, connectAddress, launchFailed) { apps, filter, address, failed -> Listing(apps, filter, address, failed) },
        repository.gamepads,
        repository.modeAState,
        repository.virtualPadState,
    ) { listing, gamepads, modeA, pad ->
        val readiness = when {
            gamepads.isNotEmpty() || pad == VirtualPadState.Active -> ControllerReadiness.READY
            modeA is ModeAState.Connected -> ControllerReadiness.LINKED_NOT_SHARED
            else -> ControllerReadiness.NOT_CONNECTED
        }
        GamesUiState(
            loading = listing.apps == null,
            filter = listing.filter,
            games = GameCatalog.visible(listing.apps.orEmpty(), listing.filter, repository.ownPackage)
                .map { GameRow(it, GameCatalog.isKnownMoga(it.packageName)) },
            readiness = readiness,
            connectAddress = listing.connectAddress,
            launchFailed = listing.launchFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesUiState())

    private data class Listing(
        val apps: List<InstalledGame>?,
        val filter: GameFilter,
        val connectAddress: String?,
        val launchFailed: String?,
    )

    /** Games come and go while Swivel is in the background, so this runs on every resume. */
    fun refresh() {
        viewModelScope.launch {
            apps.value = repository.installedGames()
            connectAddress.value = repository.pairedMogas().firstOrNull { it.verdict.mode == ControllerMode.A }?.device?.address
        }
    }

    fun setFilter(value: GameFilter) {
        filter.value = value
    }

    fun launch(game: InstalledGame) {
        launchFailed.value = if (repository.launchGame(game.packageName)) null else game.label
    }

    suspend fun icon(packageName: String, sizePx: Int): Bitmap? = repository.gameIcon(packageName, sizePx)

    /** Only from the visible screen: it starts a foreground service. */
    fun connectModeA(address: String) = repository.connectModeA(address)
}
