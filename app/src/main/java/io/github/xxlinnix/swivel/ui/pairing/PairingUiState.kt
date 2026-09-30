package io.github.xxlinnix.swivel.ui.pairing

import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.core.model.ModeReason
import io.github.xxlinnix.swivel.data.PairedMoga

/** The side of the A/B switch the user wants to pair in. */
enum class PairingTarget { MODE_A, MODE_B }

/** One screen of the pairing wizard. */
sealed interface PairingStep {
    data object ChooseMode : PairingStep
    data object NeedsPermission : PairingStep
    data object NeedsBluetooth : PairingStep

    /** Instructions for the controller, already-paired controllers, and the ways to search. */
    data object Prepare : PairingStep

    /** Android's companion-device chooser is searching or showing its list. */
    data object Choosing : PairingStep

    /** The app's own scan (Android 12 and later) is listing controllers. */
    data object Scanning : PairingStep

    data class Bonding(val address: String, val name: String?) : PairingStep
    data class Verifying(val address: String, val name: String?) : PairingStep

    /** Paired and working in Mode B. [descriptor] opens the test screen. */
    data class ReadyModeB(val name: String, val descriptor: String) : PairingStep

    /** Paired in Mode A. Talking to it arrives in milestone 2. */
    data class PairedModeA(val address: String, val name: String?) : PairingStep

    data class WrongMode(
        val address: String,
        val name: String?,
        val detected: ControllerMode,
        val reason: ModeReason,
    ) : PairingStep

    /** Paired, but the mode could not be confirmed in time. */
    data class NotConfirmed(val address: String, val name: String?) : PairingStep

    data class Failed(val problem: PairingProblem, val detail: String? = null) : PairingStep
}

enum class PairingProblem {
    NO_BLUETOOTH,
    COMPANION_FOUND_NOTHING,
    SCAN_DID_NOT_START,
    BOND_DID_NOT_START,
    BOND_FAILED,
}

data class FoundDevice(val address: String, val name: String?)

data class PairingUiState(
    val target: PairingTarget? = null,
    val step: PairingStep = PairingStep.ChooseMode,
    /** The user denied Nearby devices twice, so Android will not ask again. */
    val permissionPermanentlyDenied: Boolean = false,
    val paired: List<PairedMoga> = emptyList(),
    val found: List<FoundDevice> = emptyList(),
    val scanRunning: Boolean = false,
    val companionAvailable: Boolean = true,
    val inAppScanAvailable: Boolean = false,
)
