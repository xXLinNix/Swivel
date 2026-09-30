package io.github.xxlinnix.swivel.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Wraps a Mode A connect so that, on Android 13 and later, it first asks once to show
 * notifications. The link's foreground service works either way, but without the
 * permission its notification (and its Disconnect button) stays hidden.
 */
@Composable
fun rememberModeAConnect(connect: (address: String) -> Unit): (address: String) -> Unit {
    val context = LocalContext.current
    val currentConnect by rememberUpdatedState(connect)
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let { currentConnect(it) }
        pending = null
    }
    return remember(launcher) {
        { address: String ->
            val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsAsk) {
                pending = address
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                currentConnect(address)
            }
        }
    }
}
