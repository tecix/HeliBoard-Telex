// SPDX-License-Identifier: GPL-3.0-only
package com.viettelex

import com.viettelex.keyboard.AdjacentKeyFixer
import com.viettelex.keyboard.VNLexicon2Data
import com.viettelex.keyboard.VNSuggest
import com.viettelex.telexcore.TelexEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Engine-level behaviour behind each Vietnamese setting (no Android / HeliBoard involved). */
class TelexEngineOptionsTest {

    @Test
    fun testFreeMarkingOption() {
        val engineFree = TelexEngine().apply { freeMarking = true }
        for (ch in "thuongw") engineFree.feed(ch)
        assertEquals("thương", engineFree.composed)

        val engineStrict = TelexEngine().apply { freeMarking = false }
        for (ch in "thuongw") engineStrict.feed(ch)
        assertEquals("thuongw", engineStrict.composed)
    }

    @Test
    fun testSimpleTelexOption() {
        val engineSimple = TelexEngine().apply { simpleTelex = true }
        for (ch in "w") engineSimple.feed(ch)
        assertEquals("w", engineSimple.composed)

        val engineFull = TelexEngine().apply { simpleTelex = false }
        for (ch in "w") engineFull.feed(ch)
        assertEquals("ư", engineFull.composed)
    }

    @Test
    fun testContextualEnglishAndRetroactiveRestore() {
        val engine = TelexEngine().apply {
            contextualEnglish = true
            retroactiveEnglish = true
            eagerEnglishRestore = true
        }
        for (ch in "the") engine.feed(ch)
        assertEquals("the", engine.commitText(autoRestore = true))
        engine.noteBoundaryCharacter(' ')
        for (ch in "far") engine.feed(ch)
        // Because of English context after "the", "far" stays "far" instead of "fả"
        assertEquals("far", engine.commitText(autoRestore = true))
    }

    @Test
    fun testAutoFixAdjacentSlip() {
        assertNotNull(VNLexicon2Data.blob, "vnlexicon.bin should be loaded")
        val composer = { raw: String ->
            val e = TelexEngine().apply {
                freeMarking = true
                simpleTelex = true
                liveSpellCheck = true
                eagerEnglishRestore = true
            }
            for (c in raw) e.feed(c)
            e.composed
        }
        val matches = VNSuggest.matches("việ", poolLimit = 10)
        assertTrue(matches.isNotEmpty(), "VNSuggest should return completions for 'việ'")

        // "virjt" composes the valid word "vịt", so no correction is expected
        assertEquals(null, AdjacentKeyFixer.lexiconCorrection("virjt", composer, false))
        assertEquals("việt", AdjacentKeyFixer.lexiconCorrection("vieeth", composer, false))
        assertEquals("bạn", AdjacentKeyFixer.lexiconCorrection("bnaj", composer, false))
        assertEquals("của", AdjacentKeyFixer.lexiconCorrection("cyar", composer, false))
    }

    @Test
    fun testAutoRestoreEnglishOption() {
        val engineRestoreOn = TelexEngine().apply { eagerEnglishRestore = true }
        for (ch in "text") engineRestoreOn.feed(ch)
        assertEquals("text", engineRestoreOn.commitText(autoRestore = true))

        val engineRestoreOff = TelexEngine().apply {
            eagerEnglishRestore = false
            liveSpellCheck = false
            contextualEnglish = false
        }
        // "tẽt" is invalid (ngã on a stop final), so the engine drops the tone; use "user" instead
        for (ch in "user") engineRestoreOff.feed(ch)
        assertEquals("ủe", engineRestoreOff.commitText(autoRestore = false))
    }

    @Test
    fun testLiveSpellCheckOption() {
        val engineCheckOn = TelexEngine().apply {
            liveSpellCheck = true
            contextualEnglish = false
        }
        // "claws" has invalid Vietnamese onset "cl", so 'w'/'s' should not compose when liveSpellCheck is true
        for (ch in "claws") engineCheckOn.feed(ch)
        assertEquals("claws", engineCheckOn.composed)

        val engineCheckOff = TelexEngine().apply {
            liveSpellCheck = false
            contextualEnglish = false
            eagerEnglishRestore = false
        }
        for (ch in "claws") engineCheckOff.feed(ch)
        assertEquals("clắ", engineCheckOff.composed)
    }
}
