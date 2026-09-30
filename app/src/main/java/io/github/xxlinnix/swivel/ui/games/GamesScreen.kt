package io.github.xxlinnix.swivel.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xxlinnix.swivel.ui.ViewModelFactories
import io.github.xxlinnix.swivel.ui.rememberModeAConnect

@Composable
fun GamesScreen(
    onBack: () -> Unit,
    viewModel: GamesViewModel = viewModel(factory = ViewModelFactories.games),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val iconPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val connect = rememberModeAConnect { address -> viewModel.connectModeA(address) }
    GamesContent(
        state = state,
        onBack = onBack,
        onFilter = viewModel::setFilter,
        onLaunch = viewModel::launch,
        onConnect = connect,
        iconFor = { packageName -> viewModel.icon(packageName, iconPx)?.asImageBitmap() },
    )
}
