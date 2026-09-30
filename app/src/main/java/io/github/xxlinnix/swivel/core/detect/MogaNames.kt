package io.github.xxlinnix.swivel.core.detect

/**
 * Recognises MOGA controllers by their Bluetooth name.
 *
 * First-generation controllers (the MOGA Pocket and the original MOGA) advertise names
 * starting "BD&A", after PowerA's earlier Bensussen Deutsch & Associates brand. Later
 * ones start "MOGA" and, in Mode B, include "HID" (for example "Moga Pro 2 HID"). These
 * patterns come from the MIT-licensed moga-uinput project and from strings in the
 * original Pivot app. They are hints, not proof: see docs/RESEARCH.md.
 */
object MogaNames {
    /** The same test, as a regular expression for CompanionDeviceManager's name filter. */
    const val NAME_PATTERN = "(?i)^(moga|bd&a|bda).*"

    private val nameRegex = Regex(NAME_PATTERN)

    fun isMoga(name: String?): Boolean = name != null && nameRegex.containsMatchIn(name.trim())

    /** True when the name says the controller is in HID mode. Meaningful only for a MOGA name. */
    fun namedAsHid(name: String?): Boolean = name != null && name.uppercase().contains("HID")

    /** First-generation controllers only speak Mode A; they have no A/B switch. */
    fun isFirstGeneration(name: String?): Boolean {
        val upper = name?.trim()?.uppercase() ?: return false
        return upper.startsWith("BD&A") || upper.startsWith("BDA")
    }
}
