package io.github.xxlinnix.swivel.ui.pairing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xxlinnix.swivel.ui.BluetoothPermissions
import io.github.xxlinnix.swivel.ui.ViewModelFactories
import io.github.xxlinnix.swivel.ui.findActivity
import io.github.xxlinnix.swivel.ui.rememberModeAConnect

/** Connects the wizard to the Android pieces it needs: permission, enable and chooser dialogs. */
@SuppressLint("MissingPermission") // ACTION_REQUEST_ENABLE is only offered once BLUETOOTH_CONNECT is granted.
@Composable
fun PairingScreen(
    onOpenTest: (descriptor: String) -> Unit,
    onOpenModeATest: (address: String) -> Unit,
    onClose: () -> Unit,
    viewModel: PairingViewModel = viewModel(factory = ViewModelFactories.pairing),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[BluetoothPermissions.CONNECT] == true
        // After a second refusal Android stops asking and stops wanting a rationale.
        val activity = context.findActivity()
        val permanentlyDenied = !granted && activity != null &&
            !activity.shouldShowRequestPermissionRationale(BluetoothPermissions.CONNECT)
        viewModel.onPermissionResult(granted, permanentlyDenied)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onResume() }
    val chooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onChooserResult(result.resultCode, result.data) }

    LaunchedEffect(viewModel) {
        viewModel.chooserRequests.collect { sender ->
            chooserLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val connectModeA = rememberModeAConnect { address ->
        viewModel.connectModeA(address)
        onOpenModeATest(address)
    }

    PairingContent(
        state = state,
        actions = PairingActions(
            onChooseMode = viewModel::chooseMode,
            onRequestPermission = { permissionLauncher.launch(BluetoothPermissions.nearbyDevices) },
            onOpenAppSettings = {
                val uri = Uri.fromParts("package", context.packageName, null)
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            },
            onEnableBluetooth = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            onOpenBluetoothSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            onSearchWithCompanion = viewModel::searchWithCompanion,
            onSearchInApp = viewModel::searchInApp,
            onStopScan = viewModel::stopScan,
            onDeviceChosen = viewModel::onDeviceChosen,
            onCheckAgain = viewModel::checkAgain,
            onBackToPrepare = viewModel::backToPrepare,
            onRestart = viewModel::restart,
            onOpenTest = onOpenTest,
            onConnectModeA = connectModeA,
            onClose = onClose,
        ),
    )
}
