package io.lauren.renvault.lib

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamesTest {
    private val mask = RvNameMask(listOf(RvNameSwap("Giulia", "Luca"), RvNameSwap("Giulia Maria", "Luca Andrea")))

    @Test
    fun keepsTextWithoutHiddenNames() {
        assertEquals("Mario Rossi", mask.mask("Mario Rossi"))
        assertFalse(mask.hides("Mario Rossi"))
    }

    @Test
    fun replacesFollowingTheOriginalCase() {
        assertEquals("Luca Rossi", mask.mask("Giulia Rossi"))
        assertEquals("luca.rossi@icloud.com", mask.mask("giulia.rossi@icloud.com"))
        assertEquals("LUCA", mask.mask("GIULIA"))
        assertTrue(mask.hides("giulia.rossi@icloud.com"))
    }

    @Test
    fun prefersTheLongestHiddenName() {
        assertEquals("Ciao Luca Andrea!", mask.mask("Ciao Giulia Maria!"))
    }

    @Test
    fun replacesEveryOccurrence() {
        assertEquals("Luca e luca", mask.mask("Giulia e giulia"))
    }

    @Test
    fun emptyReplacementRemovesTheName() {
        val bare = RvNameMask(listOf(RvNameSwap("Rossi", "")))
        assertEquals("Luca ", bare.mask("Luca Rossi"))
    }

    @Test
    fun noRulesMeansNoChanges() {
        assertFalse(RvNameMask.None.active)
        assertEquals("Giulia", RvNameMask.None.mask("Giulia"))
        assertEquals("Giulia", RvNameMask(listOf(RvNameSwap("  ", "Luca"))).mask("Giulia"))
    }
}
