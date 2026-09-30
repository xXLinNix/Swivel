package io.github.xxlinnix.swivel.core.detect

import io.github.xxlinnix.swivel.core.model.ControllerMode
import io.github.xxlinnix.swivel.core.model.ModeReason
import io.github.xxlinnix.swivel.core.model.ModeVerdict
import kotlin.test.Test
import kotlin.test.assertEquals

class ModeDetectorTest {
    @Test
    fun aGamepadInputDeviceMeansModeBWhateverTheNameSays() {
        val verdict = ModeDetector.detect(
            ModeEvidence("Moga Pro 2", setOf(ModeDetector.SERIAL_PORT_UUID), gamepadPresent = true),
        )
        assertEquals(ModeVerdict(ControllerMode.B, ModeReason.GAMEPAD_INPUT_DEVICE), verdict)
    }

    @Test
    fun theHidServiceMeansModeB() {
        val verdict = ModeDetector.detect(
            ModeEvidence("Moga Pro 2", setOf(ModeDetector.SERIAL_PORT_UUID, ModeDetector.HID_UUID)),
        )
        assertEquals(ModeVerdict(ControllerMode.B, ModeReason.HID_SERVICE), verdict)
    }

    @Test
    fun theSerialServiceWithoutHidMeansModeA() {
        val verdict = ModeDetector.detect(ModeEvidence("Moga Pro 2 HID", setOf(ModeDetector.SERIAL_PORT_UUID)))
        assertEquals(ModeVerdict(ControllerMode.A, ModeReason.SERIAL_SERVICE_ONLY), verdict)
    }

    @Test
    fun uuidCaseDoesNotMatter() {
        val verdict = ModeDetector.detect(ModeEvidence(null, setOf(ModeDetector.HID_UUID.uppercase())))
        assertEquals(ControllerMode.B, verdict.mode)
    }

    @Test
    fun withNoServicesTheNameDecides() {
        assertEquals(
            ModeVerdict(ControllerMode.B, ModeReason.NAME_HINT),
            ModeDetector.detect(ModeEvidence("Moga Pro 2 HID")),
        )
        assertEquals(
            ModeVerdict(ControllerMode.A, ModeReason.NAME_HINT),
            ModeDetector.detect(ModeEvidence("BD&A 1234")),
        )
    }

    @Test
    fun anUnknownDeviceWithNoServicesIsUnknown() {
        assertEquals(
            ModeVerdict(ControllerMode.UNKNOWN, ModeReason.NO_EVIDENCE),
            ModeDetector.detect(ModeEvidence("Headphones")),
        )
    }
}
