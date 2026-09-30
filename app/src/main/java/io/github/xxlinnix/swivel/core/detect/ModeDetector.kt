package io.github.xxlinnix.swivel.core.detect

import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.core.model.ModeReason
import io.github.xxlinnix.swivel.core.model.ModeVerdict

/** What the phone knows about one paired controller. */
data class ModeEvidence(
    val bluetoothName: String?,
    /** Service UUIDs from the last SDP lookup, in any case. Empty when unknown. */
    val serviceUuids: Set<String> = emptySet(),
    /** True when Android has an input device for this controller right now. */
    val gamepadPresent: Boolean = false,
)

/**
 * Decides which side of the A/B switch a controller is on.
 *
 * The strongest evidence comes first: an input device only exists in HID mode, and the
 * SDP service list says what the controller offered when it was last asked. The name is
 * last because Android caches it from whenever the controller was paired.
 */
object ModeDetector {
    const val HID_UUID = "00001124-0000-1000-8000-00805f9b34fb"
    const val SERIAL_PORT_UUID = "00001101-0000-1000-8000-00805f9b34fb"

    fun detect(evidence: ModeEvidence): ModeVerdict {
        if (evidence.gamepadPresent) return ModeVerdict(ControllerMode.B, ModeReason.GAMEPAD_INPUT_DEVICE)

        val uuids = evidence.serviceUuids.map { it.lowercase() }.toSet()
        if (HID_UUID in uuids) return ModeVerdict(ControllerMode.B, ModeReason.HID_SERVICE)
        if (SERIAL_PORT_UUID in uuids) return ModeVerdict(ControllerMode.A, ModeReason.SERIAL_SERVICE_ONLY)

        val name = evidence.bluetoothName
        if (MogaNames.isMoga(name)) {
            val mode = if (MogaNames.namedAsHid(name)) ControllerMode.B else ControllerMode.A
            return ModeVerdict(mode, ModeReason.NAME_HINT)
        }
        return ModeVerdict(ControllerMode.UNKNOWN, ModeReason.NO_EVIDENCE)
    }
}
