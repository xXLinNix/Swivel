package io.github.xxlinnix.swivel.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xxlinnix.swivel.ui.ViewModelFactories

@Composable
fun HomeScreen(
    onPair: () -> Unit,
    onTest: (descriptor: String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = ViewModelFactories.home),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    HomeContent(state, onPair, onTest)
}
