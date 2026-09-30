package io.github.xxlinnix.swivel.core.detect

import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.core.model.ModeReason
import io.github.xxlinnix.swivel.core.model.ModeVerdict

/** What the pairing wizard should do with the evidence it has so far. */
sealed interface PairingDecision {
    /** Nothing conclusive yet; wait for SDP or for the gamepad to appear. */
    data object KeepWaiting : PairingDecision

    /** The controller is in the mode the user asked for. */
    data object Confirmed : PairingDecision

    /** The controller is on the other side of the A/B switch. */
    data class WrongMode(val detected: ControllerMode) : PairingDecision

    /** Time ran out without enough evidence either way. */
    data object NotConfirmed : PairingDecision
}

/**
 * The rules that end the wizard's verification step.
 *
 * Only strong evidence (a gamepad input device, or a fresh service list) settles the
 * question early. A name hint only counts once the wait has timed out.
 */
object PairingJudge {
    fun decide(wanted: ControllerMode, verdict: ModeVerdict, timedOut: Boolean): PairingDecision {
        require(wanted != ControllerMode.UNKNOWN) { "The wizard always pairs for A or B" }
        val strong = verdict.reason != ModeReason.NAME_HINT && verdict.reason != ModeReason.NO_EVIDENCE
        val settled = strong || timedOut
        return when {
            // In Mode B only a real input device counts: HID service alone may mean it is asleep.
            wanted == ControllerMode.B && verdict.reason == ModeReason.GAMEPAD_INPUT_DEVICE -> PairingDecision.Confirmed
            wanted == ControllerMode.B && verdict.mode == ControllerMode.B ->
                if (timedOut) PairingDecision.NotConfirmed else PairingDecision.KeepWaiting
            wanted == ControllerMode.A && verdict.mode == ControllerMode.A && settled -> PairingDecision.Confirmed
            verdict.mode != ControllerMode.UNKNOWN && verdict.mode != wanted && settled ->
                PairingDecision.WrongMode(verdict.mode)
            timedOut -> PairingDecision.NotConfirmed
            else -> PairingDecision.KeepWaiting
        }
    }

    /**
     * The service list to judge by. Android's cached list dates from the last pairing and
     * may describe the other side of the switch, so it only counts after the timeout.
     */
    fun servicesToTrust(fresh: Set<String>?, cached: Set<String>, timedOut: Boolean): Set<String> =
        fresh ?: if (timedOut) cached else emptySet()
}
