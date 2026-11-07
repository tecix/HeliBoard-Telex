// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The strip layout must place 3, 4 or 5 suggestions in distinct, valid slots ("Suggestions shown in the bar"). */
class SuggestionsInStripTest {
    /** index in SuggestedWords → slot, for the suggestions that fit, like setupWordViewsAndReturnStartIndexOfMoreSuggestions. */
    private fun slotsByIndex(count: Int, willAutoCorrect: Boolean, omitTypedWord: Boolean): Map<Int, Int> {
        val center = count / 2
        val result = LinkedHashMap<Int, Int>()
        var index = 0
        while (result.size < count) {
            val p = SuggestionStripLayoutHelper.getPositionInSuggestionStrip(index, willAutoCorrect, omitTypedWord, center, center - 1)
            if (p >= 0) result[index] = p
            index++
        }
        return result
    }

    private fun positions(count: Int, willAutoCorrect: Boolean, omitTypedWord: Boolean) =
        slotsByIndex(count, willAutoCorrect, omitTypedWord).values.toList()

    @Test fun slotsAreDistinctAndInRange() {
        for (count in 3..5) for (autoCorrect in listOf(false, true)) for (omit in listOf(false, true)) {
            val slots = slotsByIndex(count, autoCorrect, omit)
            assertEquals((0 until count).toSet(), slots.values.toSet(), "count=$count autoCorrect=$autoCorrect omit=$omit: $slots")
            // most important: the first suggestion when the typed word is omitted or autocorrect is pending,
            // otherwise the typed word
            val mostImportant = if (omit || autoCorrect) SuggestedWords.INDEX_OF_AUTO_CORRECTION else SuggestedWords.INDEX_OF_TYPED_WORD
            assertEquals(count / 2, slots[mostImportant], "count=$count autoCorrect=$autoCorrect omit=$omit: $slots")
        }
    }

    @Test fun fiveSlotsAreSymmetric() {
        // typing: index 1 center, then left/right alternating
        assertEquals(listOf(2, 1, 3, 0, 4), positions(5, willAutoCorrect = false, omitTypedWord = true))
        assertTrue(positions(5, false, false).containsAll(listOf(0, 1, 2, 3, 4)))
    }
}
