// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.viettelex

import helium314.keyboard.event.Event
import helium314.keyboard.viettelex.VietTelexCombiner
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.WordComposer
import kotlin.test.BeforeTest
import kotlin.test.Test
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.viettelex.VietTelexSettings
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class VietTelexIntegrationTest {

    /** Other Robolectric tests (InputLogicTest) leave global settings behind; start from "no settings". */
    @BeforeTest
    fun clearGlobalSettings() {
        val field = Settings::class.java.getDeclaredField("mSettingsValues").apply { isAccessible = true }
        field.set(Settings.getInstance(), null)
        VietTelexSettings.current = null
    }

    @Test
    fun testReEditWordsFeature() {
        val combiner = VietTelexCombiner()
        // 1. Seed existing word on cursor/backspace ("viêt" -> add 'j' -> "việt")
        assertTrue(combiner.canSeedAndTransform("viêt", 'j'))
        assertTrue(combiner.seedForTransform("viêt"))
        val ev = combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent('j'.code, 0, 0, 0, false))
        assertTrue(ev.isConsumed)
        assertEquals("việt", combiner.combiningStateFeedback.toString())

        // 2. Resume committed word after space ("anh " -> backspace -> "anh" + 's' -> "ánh")
        combiner.resetContext()
        for (ch in "anh") {
            combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent(ch.code, 0, 0, 0, false))
        }
        assertEquals("anh", combiner.commitBoundary())
        combiner.resumeWord("anh")
        combiner.processEvent(arrayListOf(), Event.createSoftwareKeypressEvent('s'.code, 0, 0, 0, false))
        assertEquals("ánh", combiner.combiningStateFeedback.toString())
    }

    @Test
    fun testWordComposerWithVietTelexCombinerChain() {
        val wordComposer = WordComposer()
        wordComposer.restartCombining("viet_telex")
        assertTrue(wordComposer.hasVietTelexCombiner())

        for (ch in "tieengs") {
            val event = Event.createSoftwareKeypressEvent(ch.code, 0, 0, 0, false)
            val processed = wordComposer.processEvent(event)
            wordComposer.applyProcessedEvent(processed)
        }
        assertEquals("tiếng", wordComposer.typedWord)
        assertEquals("tieengs", wordComposer.vietTelexRawKeystrokes)

        // Copy preserves raw keystrokes and hasVietTelexCombiner for Suggest
        val copy = wordComposer.copy()
        assertTrue(copy.hasVietTelexCombiner())
        assertEquals("tieengs", copy.vietTelexRawKeystrokes)
    }

    @Test
    fun testVietnameseDictionaryPresence() {
        val dict = listOf("src/main/assets/dicts/main_vi.dict", "app/src/main/assets/dicts/main_vi.dict")
            .map { java.io.File(it) }.firstOrNull { it.isFile } ?: java.io.File("src/main/assets/dicts/main_vi.dict")
        assertTrue(dict.exists(), "main_vi.dict should exist in assets/dicts")
        assertTrue(dict.length() > 100_000, "main_vi.dict should be a valid non-empty dictionary file")
    }
}
