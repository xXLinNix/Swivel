package io.github.xxlinnix.swivel.ui.test

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.xxlinnix.swivel.core.model.GamepadButton
import io.github.xxlinnix.swivel.core.model.Stick
import io.github.xxlinnix.swivel.data.input.GamepadInfo
import io.github.xxlinnix.swivel.ui.common.BodyText
import io.github.xxlinnix.swivel.ui.common.Hint
import io.github.xxlinnix.swivel.ui.common.Section
import io.github.xxlinnix.swivel.ui.common.batteryLabel
import io.github.xxlinnix.swivel.ui.common.hex4
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerTestContent(state: TestUiState, onBack: () -> Unit) {
    val info = state.info
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(info?.name ?: "Controller test") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        if (info == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                BodyText(
                    if (state.loading) "Connecting…"
                    else "The controller is disconnected. Press a button on it to wake it up and it will come back here.",
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { DeviceSection(info, state) }
            item { SticksAndTriggers(state) }
            item { Buttons(state.snapshot.pressed) }
            item { RawAxes(info, state.snapshot.rawAxes) }
            item { KeySection(state) }
        }
    }
}

@Composable
private fun DeviceSection(info: GamepadInfo, state: TestUiState) {
    Section("Device") {
        BodyText(if (info.looksLikeMoga) "MOGA in Mode B (Bluetooth HID)" else "Bluetooth or USB gamepad")
        Mono("vendor ${hex4(info.vendorId)}  product ${hex4(info.productId)}  player ${info.controllerNumber}")
        BodyText(batteryLabel(state.battery))
        Hint("Back and focus movement are off on this screen so every button can be tested. Use Back at the top.")
    }
}

@Composable
private fun SticksAndTriggers(state: TestUiState) {
    val snapshot = state.snapshot
    Section("Sticks and triggers") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StickView("Left", snapshot.leftStick, GamepadButton.L3 in snapshot.pressed, Modifier.weight(1f))
            StickView("Right", snapshot.rightStick, GamepadButton.R3 in snapshot.pressed, Modifier.weight(1f))
        }
        TriggerBar("L2", snapshot.leftTrigger, GamepadButton.L2 in snapshot.pressed)
        TriggerBar("R2", snapshot.rightTrigger, GamepadButton.R2 in snapshot.pressed)
    }
}

@Composable
private fun StickView(label: String, stick: Stick, clicked: Boolean, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val dot = if (clicked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val radius = size.minDimension / 2f
            drawCircle(outline, radius = radius - 2f, style = Stroke(width = 2f))
            drawLine(outline, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))
            drawLine(outline, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))
            val position = Offset(
                center.x + stick.x.coerceIn(-1f, 1f) * (radius - 12f),
                center.y + stick.y.coerceIn(-1f, 1f) * (radius - 12f),
            )
            drawCircle(dot, radius = if (clicked) 14f else 10f, center = position)
        }
        Text("$label stick", style = MaterialTheme.typography.labelLarge)
        Mono("x ${fmt(stick.x)}  y ${fmt(stick.y)}")
    }
}

@Composable
private fun TriggerBar(label: String, value: Float, pressed: Boolean) {
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(if (pressed) "$label (button down)" else label, style = MaterialTheme.typography.labelLarge)
            Mono(fmt(value))
        }
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}

@Composable
private fun Buttons(pressed: Set<GamepadButton>) {
    Section("Buttons") {
        ButtonRow { Lamp("L1", GamepadButton.L1 in pressed); Lamp("L2", GamepadButton.L2 in pressed); Lamp("R2", GamepadButton.R2 in pressed); Lamp("R1", GamepadButton.R1 in pressed) }
        ButtonRow { Lamp("Up", GamepadButton.DPAD_UP in pressed); Lamp("Down", GamepadButton.DPAD_DOWN in pressed); Lamp("Left", GamepadButton.DPAD_LEFT in pressed); Lamp("Right", GamepadButton.DPAD_RIGHT in pressed) }
        ButtonRow { Lamp("Y", GamepadButton.Y in pressed); Lamp("X", GamepadButton.X in pressed); Lamp("B", GamepadButton.B in pressed); Lamp("A", GamepadButton.A in pressed) }
        ButtonRow { Lamp("Select", GamepadButton.SELECT in pressed); Lamp("Start", GamepadButton.START in pressed); Lamp("L3", GamepadButton.L3 in pressed); Lamp("R3", GamepadButton.R3 in pressed) }
    }
}

@Composable
private fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun RowScope.Lamp(label: String, lit: Boolean) {
    val background = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (lit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .background(background, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RawAxes(info: GamepadInfo, values: Map<Int, Float>) {
    Section("Raw axes") {
        if (info.axes.isEmpty()) BodyText("This controller declares no joystick axes.")
        info.axes.forEach { axis ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Mono(axis.label)
                Mono("${fmt(values[axis.axis] ?: 0f)}  [${fmt(axis.min)}, ${fmt(axis.max)}] flat ${fmt(axis.flat)}")
            }
        }
    }
}

@Composable
private fun KeySection(state: TestUiState) {
    Section("Key events") {
        val unmapped = state.snapshot.unmappedKeysDown
        if (unmapped.isNotEmpty()) BodyText("Held keys with no button mapping: ${unmapped.sorted().joinToString()}")
        if (state.keyLog.isEmpty()) BodyText("Press a button.")
        state.keyLog.take(12).forEach { entry ->
            Mono("${if (entry.down) "down" else "up  "} ${entry.label} (${entry.keyCode}) scan ${entry.scanCode}")
        }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}

private fun fmt(value: Float): String = String.format(Locale.US, "%+.3f", value)
