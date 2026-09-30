package io.github.xxlinnix.swivel.core.detect

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MogaNamesTest {
    @Test
    fun recognisesEveryKnownNamingScheme() {
        assertTrue(MogaNames.isMoga("BD&A 1234"))
        assertTrue(MogaNames.isMoga("Moga Pro 2"))
        assertTrue(MogaNames.isMoga("Moga Pro 2 HID"))
        assertTrue(MogaNames.isMoga("MOGA Hero Power"))
        assertTrue(MogaNames.isMoga("  moga pocket"))
    }

    @Test
    fun rejectsOtherControllersAndMissingNames() {
        assertFalse(MogaNames.isMoga(null))
        assertFalse(MogaNames.isMoga(""))
        assertFalse(MogaNames.isMoga("Xbox Wireless Controller"))
        assertFalse(MogaNames.isMoga("My MOGA"))
    }

    @Test
    fun hidInTheNameMeansModeB() {
        assertTrue(MogaNames.namedAsHid("Moga Pro 2 HID"))
        assertFalse(MogaNames.namedAsHid("Moga Pro 2"))
    }

    @Test
    fun firstGenerationIsTheBdaPrefix() {
        assertTrue(MogaNames.isFirstGeneration("BD&A 1234"))
        assertFalse(MogaNames.isFirstGeneration("Moga Pro 2"))
    }

    @Test
    fun nameFilterPatternMatchesWhatIsMogaAccepts() {
        val regex = Regex(MogaNames.NAME_PATTERN)
        assertTrue(regex.containsMatchIn("Moga Pro 2 HID"))
        assertTrue(regex.containsMatchIn("BD&A 1234"))
        assertFalse(regex.containsMatchIn("DualSense Wireless Controller"))
    }
}
