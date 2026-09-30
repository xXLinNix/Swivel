package io.github.xxlinnix.swivel.data

import android.content.Context
import android.graphics.Bitmap
import io.github.xxlinnix.swivel.core.games.InstalledGame
import io.github.xxlinnix.swivel.data.games.InstalledGames
import android.content.Intent
import io.github.xxlinnix.swivel.core.detect.ModeDetector
import io.github.xxlinnix.swivel.core.detect.ModeEvidence
import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.model.ModeVerdict
import io.github.xxlinnix.swivel.data.bluetooth.BluetoothEvent
import io.github.xxlinnix.swivel.data.bluetooth.BluetoothGateway
import io.github.xxlinnix.swivel.data.bluetooth.CompanionPairing
import io.github.xxlinnix.swivel.data.bluetooth.PairedDevice
import io.github.xxlinnix.swivel.data.input.GamepadInfo
import io.github.xxlinnix.swivel.data.input.GamepadInputSource
import io.github.xxlinnix.swivel.data.input.KeyLogEntry
import io.github.xxlinnix.swivel.data.modea.ModeALink
import io.github.xxlinnix.swivel.data.modea.ModeAService
import io.github.xxlinnix.swivel.data.modea.ModeAState
import io.github.xxlinnix.swivel.data.virtualpad.ShizukuGate
import io.github.xxlinnix.swivel.data.virtualpad.ShizukuStatus
import io.github.xxlinnix.swivel.data.virtualpad.VirtualPad
import io.github.xxlinnix.swivel.data.virtualpad.VirtualPadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A paired MOGA and the mode the phone believes it is in. */
data class PairedMoga(val device: PairedDevice, val verdict: ModeVerdict)

/**
 * The one place view models get controller data from. It joins what Bluetooth knows
 * (pairings, services) with what the input system knows (gamepads, their input).
 */
class ControllerRepository(
    context: Context,
    private val bluetooth: BluetoothGateway,
    private val companion: CompanionPairing,
    private val input: GamepadInputSource,
    private val modeA: ModeALink,
    bridgeGames: StateFlow<Int>,
    private val shizuku: ShizukuGate,
    private val virtualPad: VirtualPad,
    private val installedGames: InstalledGames,
) {
    private val context = context.applicationContext

    val gamepads: StateFlow<List<GamepadInfo>> get() = input.gamepads
    val keyLog: StateFlow<List<KeyLogEntry>> get() = input.keyLog

    val bluetoothSupported: Boolean get() = bluetooth.isSupported
    val bluetoothEnabled: Boolean get() = bluetooth.isEnabled
    val companionAvailable: Boolean get() = companion.isAvailable

    fun hasConnectPermission(): Boolean = bluetooth.hasConnectPermission()
    fun canScanInApp(): Boolean = bluetooth.canScanInApp()

    fun bluetoothEvents(): Flow<BluetoothEvent> = bluetooth.events()
    fun associate(): Flow<CompanionPairing.Outcome> = companion.associate()
    fun addressFromChooser(resultCode: Int, data: Intent?): String? =
        companion.addressFrom(resultCode, data)

    fun snapshot(deviceId: Int): Flow<ControllerSnapshot> = input.snapshot(deviceId)
    fun battery(deviceId: Int): BatteryReading = input.battery(deviceId)
    fun setCaptureAll(capture: Boolean) {
        input.captureAll = capture
    }

    fun pairedMogas(): List<PairedMoga> =
        bluetooth.pairedMogas().map { PairedMoga(it, verdictFor(it.name, it.cachedUuids)) }

    fun paired(address: String): PairedDevice? = bluetooth.paired(address)
    fun nameOf(address: String): String? = bluetooth.nameOf(address)

    fun createBond(address: String): Boolean = bluetooth.createBond(address)
    fun refreshServices(address: String): Boolean = bluetooth.refreshServices(address)
    fun startDiscovery(): Boolean = bluetooth.startDiscovery()
    fun cancelDiscovery() = bluetooth.cancelDiscovery()

    val modeAState: StateFlow<ModeAState> get() = modeA.state
    val modeASnapshot: StateFlow<ControllerSnapshot> get() = modeA.snapshot
    val modeABattery: StateFlow<BatteryReading> get() = modeA.battery
    val modeALastReport: StateFlow<String?> get() = modeA.lastReport

    /** Games listening through the MOGA SDK bridge. */
    val bridgeGames: StateFlow<Int> = bridgeGames

    val shizukuStatus: StateFlow<ShizukuStatus> get() = shizuku.status
    val virtualPadState: StateFlow<VirtualPadState> get() = virtualPad.state
    val virtualPadEnabled: StateFlow<Boolean> get() = virtualPad.enabled

    fun setVirtualPadEnabled(enabled: Boolean) = virtualPad.setEnabled(enabled)
    fun requestShizukuPermission() = shizuku.requestPermission()
    fun refreshShizuku() = shizuku.refresh()

    suspend fun installedGames(): List<InstalledGame> = installedGames.load()
    suspend fun gameIcon(packageName: String, sizePx: Int): Bitmap? = installedGames.icon(packageName, sizePx)
    fun launchGame(packageName: String): Boolean = installedGames.launch(packageName)
    val ownPackage: String get() = context.packageName

    /** Starts the Mode A link in its foreground service. Call from a visible screen only. */
    fun connectModeA(address: String) = ModeAService.connect(context, address)

    fun disconnectModeA() = modeA.stop()

    /**
     * The gamepad input device that belongs to a paired controller. Android names the input
     * device after the Bluetooth device, so the match is by name. With [allowGuess], a lone
     * MOGA-looking gamepad also counts, for the moment just after pairing when a controller
     * can briefly report a different name.
     */
    fun gamepadFor(name: String?, allowGuess: Boolean = false): GamepadInfo? {
        val pads = input.gamepads.value
        if (name != null) {
            pads.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }?.let { return it }
        }
        return if (allowGuess) pads.singleOrNull { it.looksLikeMoga } else null
    }

    fun verdictFor(name: String?, uuids: Set<String>, allowGuess: Boolean = false): ModeVerdict =
        ModeDetector.detect(ModeEvidence(name, uuids, gamepadPresent = gamepadFor(name, allowGuess) != null))
}
