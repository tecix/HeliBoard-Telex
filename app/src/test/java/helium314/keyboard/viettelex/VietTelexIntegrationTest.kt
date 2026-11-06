// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.viettelex

import com.viettelex.keyboard.AdjacentKeyFixer
import com.viettelex.keyboard.VNLexicon2Data
import com.viettelex.keyboard.VNSuggest
import com.viettelex.telexcore.TelexEngine
import helium314.keyboard.event.Event
import helium314.keyboard.event.VietTelexCombiner
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.WordComposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VietTelexIntegrationTest {

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

        val fix = AdjacentKeyFixer.lexiconCorrection("virjt", composer, false)
            ?: AdjacentKeyFixer.lexiconToneSlip("vieeth", composer, false)
        assertNotNull(fix)
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
        for (ch in "text") engineRestoreOff.feed(ch)
        assertEquals("tẽt", engineRestoreOff.commitText(autoRestore = false))
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

    @Test
    fun testReEditWordsFeature() {
        val combiner = VietTelexCombiner()
        // 1. Seed existing word on cursor/backspace ("viêt" -> add 'j' -> "việt")
        assertTrue(combiner.canSeedAndTransform("viêt", 'j'))
        assertTrue(combiner.seedForTransform("viêt"))
        val ev = combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent('j'.code, KeyCode.NOT_SPECIFIED, 0, 0, false))
        assertTrue(ev.isConsumed)
        assertEquals("việt", combiner.combiningStateFeedback.toString())

        // 2. Resume committed word after space ("anh " -> backspace -> "anh" + 's' -> "ánh")
        combiner.resetContext()
        for (ch in "anh") {
            combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent(ch.code, KeyCode.NOT_SPECIFIED, 0, 0, false))
        }
        assertEquals("anh", combiner.commitBoundary())
        combiner.resumeWord("anh")
        combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent('s'.code, KeyCode.NOT_SPECIFIED, 0, 0, false))
        assertEquals("ánh", combiner.combiningStateFeedback.toString())
    }

    @Test
    fun testWordComposerWithVietTelexCombinerChain() {
        val wordComposer = WordComposer()
        wordComposer.restartCombining("viet_telex")
        assertTrue(wordComposer.hasVietTelexCombiner())

        for (ch in "tiengs") {
            val event = Event.createSoftwareKeypressEvent(ch.code, ch.code, 0, 0, false)
            val processed = wordComposer.processEvent(event)
            wordComposer.addInputEvent(processed)
        }
        assertEquals("tiếng", wordComposer.typedWord)
        assertEquals("tiengs", wordComposer.vietTelexRawKeystrokes)

        // Copy preserves raw keystrokes and hasVietTelexCombiner for Suggest
        val copy = wordComposer.copy()
        assertTrue(copy.hasVietTelexCombiner())
        assertEquals("tiengs", copy.vietTelexRawKeystrokes)
    }
}
