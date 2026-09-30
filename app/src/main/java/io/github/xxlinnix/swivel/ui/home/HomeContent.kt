package io.github.xxlinnix.swivel.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xxlinnix.swivel.ui.common.BodyText
import io.github.xxlinnix.swivel.ui.common.Hint
import io.github.xxlinnix.swivel.ui.common.Section
import io.github.xxlinnix.swivel.ui.common.verdictLabel
import io.github.xxlinnix.swivel.ui.common.modeAStatus
import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.data.PairedMoga
import io.github.xxlinnix.swivel.data.modea.ModeAState
import io.github.xxlinnix.swivel.data.modea.address
import io.github.xxlinnix.swivel.data.virtualpad.ShizukuStatus
import io.github.xxlinnix.swivel.data.virtualpad.VirtualPadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onPair: () -> Unit,
    onTest: (descriptor: String) -> Unit,
    onConnectModeA: (address: String) -> Unit,
    onDisconnectModeA: () -> Unit,
    onTestModeA: (address: String) -> Unit,
    onGames: () -> Unit = {},
    padActions: VirtualPadActions = VirtualPadActions(),
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Swivel") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section("Connected controllers") {
                    if (state.gamepads.isEmpty()) {
                        BodyText("No controller is connected. Turn yours on, or pair one.")
                        Hint("Mode A controllers are connected from the list below.")
                    }
                    state.gamepads.forEach { pad ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pad.name, style = MaterialTheme.typography.bodyLarge)
                                Hint(if (pad.looksLikeMoga) "MOGA in Mode B" else "Gamepad")
                            }
                            TextButton(onClick = { onTest(pad.descriptor) }) { Text("Test") }
                        }
                    }
                }
            }
            item {
                Section("Paired MOGA controllers") {
                    val paired = state.paired
                    when {
                        !state.bluetoothOn -> BodyText("Bluetooth is off.")
                        paired == null -> BodyText(
                            "Swivel needs the Nearby devices permission to list paired controllers. " +
                                "The pairing wizard asks for it.",
                        )
                        paired.isEmpty() -> BodyText("None yet.")
                        else -> paired.forEach { moga ->
                            PairedRow(moga, state.modeA, onConnectModeA, onDisconnectModeA, onTestModeA)
                        }
                    }
                    if (state.bridgeGames > 0) {
                        val games = if (state.bridgeGames == 1) "1 MOGA game is" else "${state.bridgeGames} MOGA games are"
                        Hint("$games listening through the MOGA SDK bridge.")
                    }
                }
            }
            item {
                Button(onClick = onGames, modifier = Modifier.fillMaxWidth()) { Text("Games") }
            }
            item { VirtualPadSection(state, padActions) }
            item {
                OutlinedButton(onClick = onPair, modifier = Modifier.fillMaxWidth()) { Text("Pair a controller") }
            }
            item {
                Hint("Swivel is an independent app. MOGA is a trademark of PowerA, which has no part in it.")
            }
        }
    }
}

@Composable
private fun PairedRow(
    moga: PairedMoga,
    modeA: ModeAState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTest: (String) -> Unit,
) {
    val address = moga.device.address
    val linked = modeA.address == address && modeA !is ModeAState.Stopped
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(moga.device.name ?: address, style = MaterialTheme.typography.bodyLarge)
        Hint(verdictLabel(moga.verdict))
        if (moga.verdict.mode == ControllerMode.A) {
            if (modeA.address == address) Hint(modeAStatus(modeA))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (linked) {
                    Button(onClick = { onTest(address) }) { Text("Test") }
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                } else {
                    Button(onClick = { onConnect(address) }) { Text("Connect") }
                }
            }
        }
    }
}

/** What the "Play Store games" section can ask the Android side to do. */
class VirtualPadActions(
    val onEnabled: (Boolean) -> Unit = {},
    val onGetShizuku: () -> Unit = {},
    val onOpenShizuku: () -> Unit = {},
    val onAllowInShizuku: () -> Unit = {},
)

@Composable
private fun VirtualPadSection(state: HomeUiState, actions: VirtualPadActions) {
    Section("Play Store games") {
        BodyText(
            "Swivel can show a Mode A controller to Android as a standard gamepad, so any game " +
                "that supports controllers can use it. This needs the free Shizuku app.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Share as a gamepad", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = state.virtualPadEnabled, onCheckedChange = actions.onEnabled)
        }
        when (val pad = state.virtualPad) {
            VirtualPadState.Off -> Hint("Off.")
            is VirtualPadState.WaitingForShizuku -> when (pad.status) {
                ShizukuStatus.NOT_INSTALLED -> {
                    BodyText("Install Shizuku from the Play Store, then come back here.")
                    Button(onClick = actions.onGetShizuku) { Text("Get Shizuku") }
                }
                ShizukuStatus.NOT_RUNNING -> {
                    BodyText(
                        "Start Shizuku: open it and follow \"Start via Wireless debugging\". " +
                            "Android stops it on every restart, so this is needed again after one.",
                    )
                    Button(onClick = actions.onOpenShizuku) { Text("Open Shizuku") }
                }
                ShizukuStatus.TOO_OLD -> BodyText("This version of Shizuku is too old. Update it from the Play Store.")
                ShizukuStatus.NEEDS_PERMISSION -> {
                    BodyText("Shizuku is running. Allow Swivel to use it.")
                    Button(onClick = actions.onAllowInShizuku) { Text("Allow Swivel in Shizuku") }
                }
                ShizukuStatus.READY -> Hint("Starting…")
            }
            VirtualPadState.WaitingForController ->
                Hint("Ready. Connect your controller above and it appears to games at once.")
            VirtualPadState.Starting -> Hint("Starting the virtual gamepad…")
            VirtualPadState.Active -> {
                BodyText("Games now see your controller as a standard gamepad (Xbox 360 layout).")
                Hint("It is listed under Connected controllers too; Test it to see exactly what games receive. Select acts as Back.")
            }
            is VirtualPadState.Failed -> BodyText("Could not create the gamepad: ${pad.reason}")
        }
    }
}
