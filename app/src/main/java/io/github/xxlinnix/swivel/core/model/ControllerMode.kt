package io.github.xxlinnix.swivel.core.model

/** The position of the A/B switch on the controller, as far as the phone can tell. */
enum class ControllerMode {
    /** "MOGA mode": a serial (RFCOMM) link that only MOGA-aware software understands. */
    A,

    /** Standard Bluetooth HID: Android sees an ordinary gamepad. */
    B,

    /** Not enough evidence to say. */
    UNKNOWN,
}

/** The mode the phone believes the controller is in, and the evidence that decided it. */
data class ModeVerdict(val mode: ControllerMode, val reason: ModeReason)

enum class ModeReason {
    /** Android created a gamepad input device for it, which only happens in HID mode. */
    GAMEPAD_INPUT_DEVICE,

    /** Its Bluetooth services include HID (0x1124). */
    HID_SERVICE,

    /** Its Bluetooth services include the Serial Port Profile (0x1101) and not HID. */
    SERIAL_SERVICE_ONLY,

    /** Only its Bluetooth name hints at the mode. The weakest evidence. */
    NAME_HINT,

    /** Nothing to go on yet. */
    NO_EVIDENCE,
}
