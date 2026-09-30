package io.github.xxlinnix.swivel.ui.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import io.github.xxlinnix.swivel.core.games.GameFilter
import io.github.xxlinnix.swivel.core.games.InstalledGame
import io.github.xxlinnix.swivel.ui.common.BodyText
import io.github.xxlinnix.swivel.ui.common.Hint
import io.github.xxlinnix.swivel.ui.common.Section

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesContent(
    state: GamesUiState,
    onBack: () -> Unit,
    onFilter: (GameFilter) -> Unit,
    onLaunch: (InstalledGame) -> Unit,
    onConnect: (address: String) -> Unit,
    iconFor: suspend (packageName: String) -> ImageBitmap?,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Games") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ReadinessSection(state, onConnect) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.filter == GameFilter.CONTROLLER_SUPPORT,
                        onClick = { onFilter(GameFilter.CONTROLLER_SUPPORT) },
                        label = { Text("Controller support") },
                    )
                    FilterChip(
                        selected = state.filter == GameFilter.ALL_GAMES,
                        onClick = { onFilter(GameFilter.ALL_GAMES) },
                        label = { Text("All games") },
                    )
                }
            }
            state.launchFailed?.let { label -> item { BodyText("$label could not be started.") } }
            when {
                state.loading -> item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.games.isEmpty() -> item {
                    BodyText(
                        if (state.filter == GameFilter.CONTROLLER_SUPPORT) {
                            "No installed game declares controller support. Many support controllers " +
                                "without saying so: try All games."
                        } else {
                            "No games are installed."
                        },
                    )
                }
                else -> items(state.games, key = { it.game.packageName }) { row -> GameItem(row, onLaunch, iconFor) }
            }
            if (state.filter == GameFilter.CONTROLLER_SUPPORT && state.games.isNotEmpty()) {
                item { Hint("Games that support controllers without declaring it are under All games.") }
            }
        }
    }
}

@Composable
private fun ReadinessSection(state: GamesUiState, onConnect: (String) -> Unit) {
    Section("Controller") {
        when (state.readiness) {
            ControllerReadiness.READY -> BodyText("Ready: games will see your controller.")
            ControllerReadiness.LINKED_NOT_SHARED -> BodyText(
                "Your controller is connected, but not shared as a gamepad yet. Check Play Store " +
                    "games on the home screen: Shizuku may need starting again.",
            )
            ControllerReadiness.NOT_CONNECTED -> {
                BodyText("No controller is connected. Games will not see one until it is.")
                state.connectAddress?.let { address ->
                    Button(onClick = { onConnect(address) }) { Text("Connect my controller") }
                }
            }
        }
    }
}

@Composable
private fun GameItem(
    row: GameRow,
    onLaunch: (InstalledGame) -> Unit,
    iconFor: suspend (String) -> ImageBitmap?,
) {
    val icon by produceState<ImageBitmap?>(null, row.game.packageName) { value = iconFor(row.game.packageName) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLaunch(row.game) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(48.dp)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(row.game.label, style = MaterialTheme.typography.bodyLarge)
            val tags = listOfNotNull(
                "Declares controller support".takeIf { row.game.declaresGamepad },
                "Listed by the original MOGA Pivot".takeIf { row.knownMoga },
            )
            if (tags.isNotEmpty()) Hint(tags.joinToString(" · "))
        }
        Text("Play", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
