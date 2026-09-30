package io.github.xxlinnix.swivel.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.ui.common.BodyText
import io.github.xxlinnix.swivel.ui.common.Hint
import io.github.xxlinnix.swivel.ui.common.Section
import io.github.xxlinnix.swivel.ui.common.reasonLabel
import io.github.xxlinnix.swivel.ui.common.verdictLabel

/** Everything the wizard can ask the Android side to do. */
class PairingActions(
    val onChooseMode: (PairingTarget) -> Unit = {},
    val onRequestPermission: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
    val onEnableBluetooth: () -> Unit = {},
    val onOpenBluetoothSettings: () -> Unit = {},
    val onSearchWithCompanion: () -> Unit = {},
    val onSearchInApp: () -> Unit = {},
    val onStopScan: () -> Unit = {},
    val onDeviceChosen: (String) -> Unit = {},
    val onCheckAgain: (String, String?) -> Unit = { _, _ -> },
    val onBackToPrepare: () -> Unit = {},
    val onRestart: () -> Unit = {},
    val onOpenTest: (String) -> Unit = {},
    val onClose: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingContent(state: PairingUiState, actions: PairingActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair a controller") },
                navigationIcon = { TextButton(onClick = actions.onClose) { Text("Close") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val step = state.step) {
                PairingStep.ChooseMode -> ChooseMode(actions)
                PairingStep.NeedsPermission -> NeedsPermission(state.permissionPermanentlyDenied, actions)
                PairingStep.NeedsBluetooth -> NeedsBluetooth(actions)
                PairingStep.Prepare -> Prepare(state, actions)
                PairingStep.Choosing -> Choosing(actions)
                PairingStep.Scanning -> Scanning(state, actions)
                is PairingStep.Bonding -> Bonding(step.name)
                is PairingStep.Verifying -> Verifying(step.name, state.target)
                is PairingStep.ReadyModeB -> ReadyModeB(step, actions)
                is PairingStep.PairedModeA -> PairedModeA(step, actions)
                is PairingStep.WrongMode -> WrongMode(step, actions)
                is PairingStep.NotConfirmed -> NotConfirmed(step, state.target, actions)
                is PairingStep.Failed -> Failed(step, actions)
            }
        }
    }
}

@Composable
private fun ChooseMode(actions: PairingActions) {
    BodyText("MOGA controllers have an A/B switch. Which mode do you want to pair in?")
    Section("Mode B: standard gamepad") {
        BodyText(
            "Works with any Android game that supports controllers, with no app running. " +
                "Choose this unless you want to play an old MOGA-enhanced game.",
        )
        Button(onClick = { actions.onChooseMode(PairingTarget.MODE_B) }) { Text("Pair in Mode B") }
    }
    Section("Mode A: MOGA mode") {
        BodyText(
            "The original MOGA protocol, for games built with the MOGA SDK. Swivel can pair a " +
                "Mode A controller now; talking to it arrives in the next milestone.",
        )
        Hint("A MOGA Pocket has no switch and only works in Mode A.")
        OutlinedButton(onClick = { actions.onChooseMode(PairingTarget.MODE_A) }) { Text("Pair in Mode A") }
    }
}

@Composable
private fun NeedsPermission(permanentlyDenied: Boolean, actions: PairingActions) {
    Section("Allow Nearby devices") {
        BodyText(
            "Swivel needs the Nearby devices permission to pair with your controller and read its " +
                "name. It never uses Bluetooth to work out where you are.",
        )
        if (permanentlyDenied) {
            BodyText("Android will not ask again. Open Swivel's settings, then Permissions, Nearby devices, Allow.")
            Button(onClick = actions.onOpenAppSettings) { Text("Open app settings") }
        } else {
            Button(onClick = actions.onRequestPermission) { Text("Allow") }
        }
    }
}

@Composable
private fun NeedsBluetooth(actions: PairingActions) {
    Section("Bluetooth is off") {
        BodyText("Turn Bluetooth on to pair your controller.")
        Button(onClick = actions.onEnableBluetooth) { Text("Turn on Bluetooth") }
    }
}

@Composable
private fun Prepare(state: PairingUiState, actions: PairingActions) {
    val letter = if (state.target == PairingTarget.MODE_A) "A" else "B"
    Section("Get the controller ready") {
        BodyText("1. Slide the switch on the controller to $letter.")
        BodyText(
            "2. Turn it on. If it has never been paired, or was last used with another phone, its " +
                "lights blink while it looks for a device. If it connects to another phone or " +
                "tablet instead, turn Bluetooth off on that device first.",
        )
        BodyText("3. Find it:")
        if (state.companionAvailable) {
            Button(onClick = actions.onSearchWithCompanion, modifier = Modifier.fillMaxWidth()) {
                Text("Find my controller")
            }
        }
        if (state.inAppScanAvailable) {
            OutlinedButton(onClick = actions.onSearchInApp, modifier = Modifier.fillMaxWidth()) {
                Text("Search inside Swivel")
            }
        }
        OutlinedButton(onClick = actions.onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Pair in Android's Bluetooth settings")
        }
        Hint("After pairing in Android's settings, come back here and pick it from the list below.")
    }
    Section("Already paired") {
        if (state.paired.isEmpty()) {
            BodyText("No MOGA controller is paired with this phone yet.")
        }
        state.paired.forEach { moga ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(moga.device.name ?: moga.device.address, style = MaterialTheme.typography.bodyLarge)
                    Hint(verdictLabel(moga.verdict))
                }
                TextButton(onClick = { actions.onDeviceChosen(moga.device.address) }) { Text("Use") }
            }
        }
    }
}

@Composable
private fun Choosing(actions: PairingActions) {
    Waiting("Looking for MOGA controllers. Android will show you a list to pick from.")
    Hint(
        "Nothing showing up? Make sure the controller's lights are blinking. On Android 11 and " +
            "older, Location must be switched on for the search to work.",
    )
    OutlinedButton(onClick = actions.onBackToPrepare) { Text("Back") }
}

@Composable
private fun Scanning(state: PairingUiState, actions: PairingActions) {
    Section("Controllers nearby") {
        if (state.found.isEmpty()) {
            BodyText(if (state.scanRunning) "Searching…" else "No MOGA controller found.")
        }
        state.found.forEach { device ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(device.name ?: device.address, modifier = Modifier.weight(1f))
                TextButton(onClick = { actions.onDeviceChosen(device.address) }) { Text("Pair") }
            }
        }
        if (state.scanRunning) {
            CircularProgressIndicator()
            OutlinedButton(onClick = actions.onStopScan) { Text("Stop") }
        } else {
            Button(onClick = actions.onSearchInApp) { Text("Search again") }
        }
    }
    OutlinedButton(onClick = actions.onBackToPrepare) { Text("Back") }
}

@Composable
private fun Bonding(name: String?) {
    Waiting("Pairing with ${name ?: "the controller"}…")
    Hint("If Android asks for a PIN, enter 1234. Older MOGA controllers use it.")
}

@Composable
private fun Verifying(name: String?, target: PairingTarget?) {
    val waitingFor = if (target == PairingTarget.MODE_A) "answer in Mode A" else "connect as a gamepad"
    Waiting("Paired. Waiting for ${name ?: "the controller"} to $waitingFor…")
    Hint("If this takes more than a few seconds, press any button on the controller to wake it.")
}

@Composable
private fun ReadyModeB(step: PairingStep.ReadyModeB, actions: PairingActions) {
    Section("Ready") {
        BodyText("${step.name} is connected in Mode B. Android games that support controllers will see it.")
        Button(onClick = { actions.onOpenTest(step.descriptor) }) { Text("Test every button") }
        OutlinedButton(onClick = actions.onClose) { Text("Done") }
    }
}

@Composable
private fun PairedModeA(step: PairingStep.PairedModeA, actions: PairingActions) {
    Section("Paired in Mode A") {
        BodyText(
            "${step.name ?: "The controller"} is paired and answering in Mode A. Swivel cannot talk " +
                "to Mode A yet: that is the next milestone.",
        )
        BodyText("Until then, slide the switch to B to use it as a standard gamepad.")
        Button(onClick = actions.onClose) { Text("Done") }
    }
}

@Composable
private fun WrongMode(step: PairingStep.WrongMode, actions: PairingActions) {
    val name = step.name ?: "The controller"
    val wanted = if (step.detected == ControllerMode.A) "B" else "A"
    Section("The switch is on ${step.detected}") {
        BodyText("$name is in Mode ${step.detected} (${reasonLabel(step.reason)}), but you asked for Mode $wanted.")
        BodyText("Turn the controller off, slide the switch to $wanted, turn it on again, then check again.")
        Hint(
            "Still wrong after that? Forget \"$name\" in Android's Bluetooth settings and pair again. " +
                "A MOGA can look like a different device in each mode.",
        )
        Button(onClick = { actions.onCheckAgain(step.address, step.name) }) { Text("Check again") }
        OutlinedButton(onClick = actions.onOpenBluetoothSettings) { Text("Open Bluetooth settings") }
        TextButton(onClick = actions.onRestart) { Text("Start over") }
    }
}

@Composable
private fun NotConfirmed(step: PairingStep.NotConfirmed, target: PairingTarget?, actions: PairingActions) {
    val letter = if (target == PairingTarget.MODE_A) "A" else "B"
    Section("Paired, but not confirmed") {
        BodyText(
            "${step.name ?: "The controller"} is paired, but Swivel could not confirm it is in Mode " +
                "$letter. It may have gone to sleep.",
        )
        BodyText("Check the switch is on $letter, press any button to wake it, then check again.")
        Button(onClick = { actions.onCheckAgain(step.address, step.name) }) { Text("Check again") }
        OutlinedButton(onClick = actions.onOpenBluetoothSettings) { Text("Open Bluetooth settings") }
        TextButton(onClick = actions.onRestart) { Text("Start over") }
    }
}

@Composable
private fun Failed(step: PairingStep.Failed, actions: PairingActions) {
    val message = when (step.problem) {
        PairingProblem.NO_BLUETOOTH -> "This device has no Bluetooth."
        PairingProblem.COMPANION_FOUND_NOTHING ->
            "Android did not find a MOGA controller. Check that its lights are blinking, or pair it " +
                "in Android's Bluetooth settings instead."
        PairingProblem.SCAN_DID_NOT_START -> "The search did not start. Try again, or use Android's Bluetooth settings."
        PairingProblem.BOND_DID_NOT_START -> "Android refused to start pairing. Try again, or use Android's Bluetooth settings."
        PairingProblem.BOND_FAILED ->
            "Pairing did not finish. It was cancelled, or the controller stopped looking for a phone. " +
                "Turn it off and on again, then retry."
    }
    Section("That didn't work") {
        BodyText(message)
        step.detail?.let { Hint("Android said: $it") }
        if (step.problem != PairingProblem.NO_BLUETOOTH) {
            Button(onClick = actions.onBackToPrepare) { Text("Try again") }
            OutlinedButton(onClick = actions.onOpenBluetoothSettings) { Text("Open Bluetooth settings") }
        }
        TextButton(onClick = actions.onRestart) { Text("Start over") }
    }
}

@Composable
private fun Waiting(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        BodyText(text)
    }
}
