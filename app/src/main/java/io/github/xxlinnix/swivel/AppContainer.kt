package io.github.xxlinnix.swivel

import android.content.Context
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.data.bluetooth.BluetoothGateway
import io.github.xxlinnix.swivel.data.bluetooth.CompanionPairing
import io.github.xxlinnix.swivel.data.input.GamepadInputSource

/**
 * Builds the app's long-lived objects once. Constructor injection by hand: the graph is
 * small, and a DI library would be a dependency to ask about (docs/DECISIONS.md, D-005).
 */
class AppContainer(context: Context) {
    val input = GamepadInputSource(context)
    val repository = ControllerRepository(BluetoothGateway(context), CompanionPairing(context), input)
}
