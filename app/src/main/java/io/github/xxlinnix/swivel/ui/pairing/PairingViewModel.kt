package io.github.xxlinnix.swivel.ui.pairing

import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xxlinnix.swivel.core.detect.MogaNames
import io.github.xxlinnix.swivel.core.detect.PairingDecision
import io.github.xxlinnix.swivel.core.detect.PairingJudge
import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.data.bluetooth.Bond
import io.github.xxlinnix.swivel.data.bluetooth.BluetoothEvent
import io.github.xxlinnix.swivel.data.bluetooth.CompanionPairing
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The pairing wizard: permission, Bluetooth on, find, bond, then confirm the mode.
 *
 * Confirming the mode is the part the original Pivot app did badly on newer phones. In
 * Mode B, success is Android creating a gamepad input device. Mode A shows up as a
 * controller offering only the serial service. See docs/ARCHITECTURE.md for the flow.
 */
class PairingViewModel(private val repository: ControllerRepository) : ViewModel() {
    private val _state = MutableStateFlow(PairingUiState(companionAvailable = repository.companionAvailable))
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private val chooserChannel = Channel<IntentSender>(Channel.BUFFERED)

    /** Each one is launched with StartIntentSenderForResult; the result goes to [onChooserResult]. */
    val chooserRequests: Flow<IntentSender> = chooserChannel.receiveAsFlow()

    private var companionJob: Job? = null
    private var timeoutJob: Job? = null

    /** Service lists from SDP lookups made during this wizard, by address. */
    private val freshUuids = mutableMapOf<String, Set<String>>()

    private var bondWatchJob: Job? = null

    init {
        viewModelScope.launch { repository.bluetoothEvents().collect { onBluetoothEvent(it) } }
        viewModelScope.launch { repository.gamepads.collect { conclude(timedOut = false) } }
        viewModelScope.launch {
            state.map { it.step }.distinctUntilChanged().collect { Log.i(TAG, "step: $it") }
        }
    }

    fun chooseMode(target: PairingTarget) {
        _state.update { it.copy(target = target) }
        advance()
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        if (granted) advance() else _state.update { it.copy(permissionPermanentlyDenied = permanentlyDenied) }
    }

    /** Called whenever the screen resumes, since permissions and pairings change in Settings. */
    fun onResume() {
        when (_state.value.step) {
            PairingStep.NeedsPermission, PairingStep.NeedsBluetooth, PairingStep.Prepare -> advance()
            else -> Unit
        }
    }

    fun restart() {
        bondWatchJob?.cancel()
        stopSearching()
        timeoutJob?.cancel()
        _state.update { PairingUiState(companionAvailable = repository.companionAvailable) }
    }

    fun backToPrepare() {
        stopSearching()
        timeoutJob?.cancel()
        advance()
    }

    fun searchWithCompanion() {
        stopSearching()
        _state.update { it.copy(step = PairingStep.Choosing) }
        companionJob = viewModelScope.launch {
            repository.associate().collect { outcome ->
                when (outcome) {
                    is CompanionPairing.Outcome.ShowChooser -> chooserChannel.send(outcome.sender)
                    is CompanionPairing.Outcome.Associated -> onDeviceChosen(outcome.address)
                    is CompanionPairing.Outcome.Failed -> _state.update {
                        it.copy(step = PairingStep.Failed(PairingProblem.COMPANION_FOUND_NOTHING, outcome.message))
                    }
                }
            }
        }
    }

    fun onChooserResult(resultCode: Int, data: Intent?) {
        val address = repository.addressFromChooser(resultCode, data)
        when {
            address != null -> onDeviceChosen(address)
            _state.value.step == PairingStep.Choosing -> backToPrepare()
        }
    }

    fun searchInApp() {
        stopSearching()
        if (!repository.startDiscovery()) {
            _state.update { it.copy(step = PairingStep.Failed(PairingProblem.SCAN_DID_NOT_START)) }
            return
        }
        _state.update { it.copy(step = PairingStep.Scanning, found = emptyList(), scanRunning = true) }
    }

    fun stopScan() {
        repository.cancelDiscovery()
        _state.update { it.copy(scanRunning = false) }
    }

    /** Used for a controller from the scan list, a pairing that already exists, or the chooser. */
    fun onDeviceChosen(address: String) {
        val step = _state.value.step
        val alreadyHandling = (step is PairingStep.Bonding && step.address == address) ||
            (step is PairingStep.Verifying && step.address == address)
        if (alreadyHandling) return

        stopSearching()
        val name = repository.nameOf(address)
        if (repository.paired(address) != null) {
            verify(address, name)
        } else if (repository.createBond(address)) {
            _state.update { it.copy(step = PairingStep.Bonding(address, name)) }
            watchBond(address, name)
        } else {
            _state.update { it.copy(step = PairingStep.Failed(PairingProblem.BOND_DID_NOT_START)) }
        }
    }

    /**
     * Checks the bond directly while bonding, in case its broadcast never arrives. The
     * first hardware test hung here for exactly that reason. Gives up after
     * [BOND_TIMEOUT_MS], which leaves time to type a PIN.
     */
    private fun watchBond(address: String, name: String?) {
        bondWatchJob?.cancel()
        bondWatchJob = viewModelScope.launch {
            var waited = 0L
            while (waited < BOND_TIMEOUT_MS) {
                delay(BOND_POLL_MS)
                waited += BOND_POLL_MS
                val step = _state.value.step
                if (step !is PairingStep.Bonding || step.address != address) return@launch
                if (repository.paired(address) != null) {
                    verify(address, repository.nameOf(address) ?: name)
                    return@launch
                }
            }
            if (_state.value.step == PairingStep.Bonding(address, name)) {
                _state.update { it.copy(step = PairingStep.Failed(PairingProblem.BOND_FAILED)) }
            }
        }
    }

    /** "Check again" after the user moved the switch or woke the controller. */
    fun checkAgain(address: String, name: String?) {
        freshUuids.remove(address)
        verify(address, name)
    }

    private fun advance() {
        val step = when {
            _state.value.target == null -> PairingStep.ChooseMode
            !repository.bluetoothSupported -> PairingStep.Failed(PairingProblem.NO_BLUETOOTH)
            !repository.hasConnectPermission() -> PairingStep.NeedsPermission
            !repository.bluetoothEnabled -> PairingStep.NeedsBluetooth
            else -> PairingStep.Prepare
        }
        _state.update {
            it.copy(
                step = step,
                permissionPermanentlyDenied = if (step == PairingStep.NeedsPermission) it.permissionPermanentlyDenied else false,
                paired = if (step == PairingStep.Prepare) repository.pairedMogas() else it.paired,
                companionAvailable = repository.companionAvailable,
                inAppScanAvailable = repository.canScanInApp(),
            )
        }
    }

    private fun onBluetoothEvent(event: BluetoothEvent) {
        val step = _state.value.step
        when (event) {
            is BluetoothEvent.AdapterChanged -> {
                if (event.enabled && step == PairingStep.NeedsBluetooth) advance()
                if (!event.enabled && step != PairingStep.ChooseMode && step !is PairingStep.Failed) advance()
            }
            is BluetoothEvent.BondChanged -> {
                if (step !is PairingStep.Bonding || step.address != event.address) return
                when (event.bond) {
                    Bond.BONDED -> verify(event.address, event.name ?: step.name)
                    Bond.NONE -> _state.update { it.copy(step = PairingStep.Failed(PairingProblem.BOND_FAILED)) }
                    Bond.BONDING -> Unit
                }
            }
            is BluetoothEvent.LinkConnected -> {
                // A sleeping controller cannot answer SDP; ask again once it connects.
                if (step is PairingStep.Verifying && step.address == event.address) repository.refreshServices(event.address)
            }
            is BluetoothEvent.ServicesDiscovered -> {
                if (event.uuids.isNotEmpty()) freshUuids[event.address] = event.uuids
                conclude(timedOut = false)
            }
            is BluetoothEvent.DeviceFound -> {
                if (step != PairingStep.Scanning || !MogaNames.isMoga(event.name)) return
                _state.update { state ->
                    if (state.found.any { it.address == event.address }) state
                    else state.copy(found = state.found + FoundDevice(event.address, event.name))
                }
            }
            BluetoothEvent.DiscoveryFinished -> _state.update { it.copy(scanRunning = false) }
            is BluetoothEvent.LinkDisconnected -> Unit
        }
    }

    private fun verify(address: String, name: String?) {
        bondWatchJob?.cancel()
        _state.update { it.copy(step = PairingStep.Verifying(address, name)) }
        repository.refreshServices(address)
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(VERIFY_TIMEOUT_MS)
            conclude(timedOut = true)
        }
        conclude(timedOut = false)
    }

    /** Ends the verification step once [PairingJudge] has an answer. */
    private fun conclude(timedOut: Boolean) {
        val current = _state.value
        val step = current.step as? PairingStep.Verifying ?: return
        val wanted = when (current.target) {
            PairingTarget.MODE_A -> ControllerMode.A
            PairingTarget.MODE_B -> ControllerMode.B
            null -> return
        }
        val cached = if (timedOut) repository.paired(step.address)?.cachedUuids.orEmpty() else emptySet()
        val uuids = PairingJudge.servicesToTrust(freshUuids[step.address], cached, timedOut)
        val verdict = repository.verdictFor(step.name, uuids, allowGuess = true)

        val next: PairingStep = when (val decision = PairingJudge.decide(wanted, verdict, timedOut)) {
            PairingDecision.KeepWaiting -> return
            PairingDecision.Confirmed -> if (wanted == ControllerMode.B) {
                val pad = repository.gamepadFor(step.name, allowGuess = true) ?: return
                PairingStep.ReadyModeB(pad.name, pad.descriptor)
            } else {
                PairingStep.PairedModeA(step.address, step.name)
            }
            is PairingDecision.WrongMode -> PairingStep.WrongMode(step.address, step.name, decision.detected, verdict.reason)
            PairingDecision.NotConfirmed -> PairingStep.NotConfirmed(step.address, step.name)
        }
        timeoutJob?.cancel()
        _state.update { it.copy(step = next) }
    }

    private fun stopSearching() {
        companionJob?.cancel()
        companionJob = null
        if (_state.value.scanRunning) stopScan()
    }

    override fun onCleared() {
        repository.cancelDiscovery()
    }

    private companion object {
        const val TAG = "Swivel"

        /** Long enough for a controller to wake, connect and answer SDP. */
        const val VERIFY_TIMEOUT_MS = 20_000L

        const val BOND_POLL_MS = 1_000L
        const val BOND_TIMEOUT_MS = 60_000L
    }
}
