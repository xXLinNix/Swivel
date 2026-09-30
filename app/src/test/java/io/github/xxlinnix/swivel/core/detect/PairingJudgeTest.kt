package io.github.xxlinnix.swivel.core.detect

import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.core.model.ModeReason
import io.github.xxlinnix.swivel.core.model.ModeVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PairingJudgeTest {
    private fun verdict(mode: ControllerMode, reason: ModeReason) = ModeVerdict(mode, reason)

    @Test
    fun modeBIsConfirmedOnlyByAGamepad() {
        assertEquals(
            PairingDecision.Confirmed,
            PairingJudge.decide(ControllerMode.B, verdict(ControllerMode.B, ModeReason.GAMEPAD_INPUT_DEVICE), timedOut = false),
        )
        assertEquals(
            PairingDecision.KeepWaiting,
            PairingJudge.decide(ControllerMode.B, verdict(ControllerMode.B, ModeReason.HID_SERVICE), timedOut = false),
        )
        assertEquals(
            PairingDecision.NotConfirmed,
            PairingJudge.decide(ControllerMode.B, verdict(ControllerMode.B, ModeReason.HID_SERVICE), timedOut = true),
        )
    }

    @Test
    fun aSerialOnlyControllerIsTheWrongModeForBAtOnce() {
        assertEquals(
            PairingDecision.WrongMode(ControllerMode.A),
            PairingJudge.decide(ControllerMode.B, verdict(ControllerMode.A, ModeReason.SERIAL_SERVICE_ONLY), timedOut = false),
        )
    }

    @Test
    fun aNameHintWaitsForTheTimeout() {
        val nameSaysA = verdict(ControllerMode.A, ModeReason.NAME_HINT)
        assertEquals(PairingDecision.KeepWaiting, PairingJudge.decide(ControllerMode.B, nameSaysA, timedOut = false))
        assertEquals(PairingDecision.WrongMode(ControllerMode.A), PairingJudge.decide(ControllerMode.B, nameSaysA, timedOut = true))
        assertEquals(PairingDecision.KeepWaiting, PairingJudge.decide(ControllerMode.A, nameSaysA, timedOut = false))
        assertEquals(PairingDecision.Confirmed, PairingJudge.decide(ControllerMode.A, nameSaysA, timedOut = true))
    }

    @Test
    fun aGamepadIsTheWrongModeForA() {
        assertEquals(
            PairingDecision.WrongMode(ControllerMode.B),
            PairingJudge.decide(ControllerMode.A, verdict(ControllerMode.B, ModeReason.GAMEPAD_INPUT_DEVICE), timedOut = false),
        )
    }

    @Test
    fun noEvidenceEndsUnconfirmed() {
        val nothing = verdict(ControllerMode.UNKNOWN, ModeReason.NO_EVIDENCE)
        assertEquals(PairingDecision.KeepWaiting, PairingJudge.decide(ControllerMode.A, nothing, timedOut = false))
        assertEquals(PairingDecision.NotConfirmed, PairingJudge.decide(ControllerMode.A, nothing, timedOut = true))
        assertEquals(PairingDecision.NotConfirmed, PairingJudge.decide(ControllerMode.B, nothing, timedOut = true))
    }

    @Test
    fun theWizardNeverAsksForUnknown() {
        assertFailsWith<IllegalArgumentException> {
            PairingJudge.decide(ControllerMode.UNKNOWN, verdict(ControllerMode.A, ModeReason.NAME_HINT), timedOut = false)
        }
    }

    @Test
    fun cachedServicesAreIgnoredUntilTheTimeout() {
        val cached = setOf(ModeDetector.SERIAL_PORT_UUID)
        val fresh = setOf(ModeDetector.HID_UUID)
        assertEquals(emptySet(), PairingJudge.servicesToTrust(null, cached, timedOut = false))
        assertEquals(cached, PairingJudge.servicesToTrust(null, cached, timedOut = true))
        assertEquals(fresh, PairingJudge.servicesToTrust(fresh, cached, timedOut = false))
    }
}
