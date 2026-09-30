package io.github.xxlinnix.swivel.ui.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xxlinnix.swivel.ui.ViewModelFactories

@Composable
fun ControllerTestScreen(
    onBack: () -> Unit,
    viewModel: ControllerTestViewModel = viewModel(factory = ViewModelFactories.test),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setCapture(true)
        onDispose { viewModel.setCapture(false) }
    }
    ControllerTestContent(state, onBack)
}
