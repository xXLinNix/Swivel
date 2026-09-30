package io.github.xxlinnix.swivel.data.bluetooth

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.xxlinnix.swivel.core.detect.MogaNames
import java.util.regex.Pattern
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Finds a controller through Android's companion-device chooser.
 *
 * The system does the scan and shows the user a list filtered to MOGA names, so the app
 * needs neither BLUETOOTH_SCAN nor a location permission for it, on any Android version.
 * The association it creates is also what lets a later milestone restart the Mode A
 * connection from the background
 * (developer.android.com/develop/connectivity/bluetooth/companion-device-pairing).
 */
class CompanionPairing(context: Context) {
    private val manager: CompanionDeviceManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            context.getSystemService(CompanionDeviceManager::class.java)
        } else {
            null
        }

    val isAvailable: Boolean get() = manager != null

    sealed interface Outcome {
        /** Launch this with StartIntentSenderForResult and hand the result to [addressFrom]. */
        data class ShowChooser(val sender: IntentSender) : Outcome

        /** Android 13 and later also report the association here. */
        data class Associated(val address: String) : Outcome

        /** The chooser found nothing, or companion pairing is unavailable. */
        data class Failed(val message: String?) : Outcome
    }

    /** Starts a search. The flow ends after a failure; cancel it to stop listening. */
    fun associate(): Flow<Outcome> = callbackFlow {
        val manager = manager
        if (manager == null) {
            trySend(Outcome.Failed(null))
            close()
            awaitClose { }
            return@callbackFlow
        }
        val filter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile(MogaNames.NAME_PATTERN))
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(false)
            .build()
        val callback = object : CompanionDeviceManager.Callback() {
            @Deprecated("Replaced by onAssociationPending on Android 13")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                trySend(Outcome.ShowChooser(chooserLauncher))
            }

            override fun onAssociationPending(intentSender: IntentSender) {
                trySend(Outcome.ShowChooser(intentSender))
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                associationInfo.deviceMacAddress?.let { trySend(Outcome.Associated(it.toString().uppercase())) }
            }

            override fun onFailure(error: CharSequence?) {
                trySend(Outcome.Failed(error?.toString()))
                close()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.associate(request, { it.run() }, callback)
        } else {
            @Suppress("DEPRECATION")
            manager.associate(request, callback, Handler(Looper.getMainLooper()))
        }
        awaitClose { }
    }

    /** The chosen controller's address from the chooser's result, or null if the user backed out. */
    fun addressFrom(resultCode: Int, data: Intent?): String? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.parcelable(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
                ?.deviceMacAddress
                ?.let { return it.toString().uppercase() }
        }
        @Suppress("DEPRECATION")
        return data.parcelable(CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)?.address
    }
}
