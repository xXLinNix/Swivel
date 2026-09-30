package io.github.xxlinnix.swivel.ui.home

import androidx.compose.runtime.Composable
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xxlinnix.swivel.data.virtualpad.ShizukuGate
import io.github.xxlinnix.swivel.ui.ViewModelFactories
import io.github.xxlinnix.swivel.ui.rememberModeAConnect

@Composable
fun HomeScreen(
    onPair: () -> Unit,
    onTest: (descriptor: String) -> Unit,
    onTestModeA: (address: String) -> Unit,
    onGames: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = ViewModelFactories.home),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val connect = rememberModeAConnect { address ->
        viewModel.connectModeA(address)
        onTestModeA(address)
    }
    HomeContent(
        state = state,
        onPair = onPair,
        onTest = onTest,
        onConnectModeA = connect,
        onDisconnectModeA = viewModel::disconnectModeA,
        onTestModeA = onTestModeA,
        onGames = onGames,
        padActions = VirtualPadActions(
            onEnabled = viewModel::setVirtualPadEnabled,
            onGetShizuku = {
                val uri = Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuGate.SHIZUKU_PACKAGE}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            onOpenShizuku = {
                context.packageManager.getLaunchIntentForPackage(ShizukuGate.SHIZUKU_PACKAGE)?.let { context.startActivity(it) }
            },
            onAllowInShizuku = viewModel::allowInShizuku,
        ),
    )
}
