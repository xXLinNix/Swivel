package io.github.xxlinnix.swivel

import android.content.Context
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.data.bluetooth.BluetoothGateway
import io.github.xxlinnix.swivel.data.bluetooth.CompanionPairing
import io.github.xxlinnix.swivel.data.input.GamepadInputSource
import io.github.xxlinnix.swivel.data.modea.ModeALink
import io.github.xxlinnix.swivel.data.virtualpad.ShizukuGate
import io.github.xxlinnix.swivel.data.virtualpad.VirtualPad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Builds the app's long-lived objects once. Constructor injection by hand: the graph is
 * small, and a DI library would be a dependency to ask about (docs/DECISIONS.md, D-005).
 */
class AppContainer(context: Context) {
    /** Work that outlives any screen, such as the Mode A link. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val input = GamepadInputSource(context)
    val bluetooth = BluetoothGateway(context)
    val modeALink = ModeALink(context, appScope)
    /** How many games are listening through the MOGA SDK bridge. Set by MogaSdkService. */
    val bridgeGames = MutableStateFlow(0)

    val shizuku = ShizukuGate(context)
    val virtualPad = VirtualPad(context, appScope, shizuku, modeALink)

    val repository = ControllerRepository(context, bluetooth, CompanionPairing(context), input, modeALink, bridgeGames, shizuku, virtualPad)
}
