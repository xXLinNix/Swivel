package io.github.xxlinnix.swivel.ui

import android.Manifest
import android.os.Build

/**
 * Android 12 split Bluetooth into runtime permissions. Requested together, CONNECT and
 * SCAN appear as one "Nearby devices" prompt. Earlier versions grant Bluetooth at install
 * time, so there is nothing to request.
 */
object BluetoothPermissions {
    const val CONNECT = Manifest.permission.BLUETOOTH_CONNECT

    val nearbyDevices: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyArray()
        }
}
