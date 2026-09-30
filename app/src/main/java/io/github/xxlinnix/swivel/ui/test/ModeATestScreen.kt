package io.github.xxlinnix.swivel.ui.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xxlinnix.swivel.ui.ViewModelFactories
import io.github.xxlinnix.swivel.ui.rememberModeAConnect

@Composable
fun ModeATestScreen(
    onBack: () -> Unit,
    viewModel: ModeATestViewModel = viewModel(factory = ViewModelFactories.modeATest),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setCapture(true)
        onDispose { viewModel.setCapture(false) }
    }
    val connect = rememberModeAConnect { viewModel.reconnect() }
    ControllerTestContent(state, onBack, onReconnect = { connect(viewModel.address) })
}
