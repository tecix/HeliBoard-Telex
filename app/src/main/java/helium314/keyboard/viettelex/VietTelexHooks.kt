// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.viettelex

import android.content.Context
import com.viettelex.keyboard.AdjacentKeyFixer
import com.viettelex.keyboard.KeyboardData
import com.viettelex.keyboard.ReEdit
import com.viettelex.keyboard.VNSuggest
import helium314.keyboard.event.Event
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.inputlogic.SpaceState
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import java.util.Locale

/**
 * Every place where HeliBoard code calls into Vietnamese Telex goes through this object, so the
 * upstream files only carry one-line hook calls. Keep the logic here, not in the upstream files.
 */
object VietTelexHooks {
    const val COMBINING_SPEC = "viet_telex"

    // ---- LatinIME ----

    /** Called from LatinIME.onCreate: lexicon asset access and preference snapshot. */
    @JvmStatic
    fun onCreate(context: Context) {
        val assets = context.applicationContext.assets
        KeyboardData.install { name -> runCatching { assets.open(name) }.getOrNull() }
        VietTelexSettings.install(context)
    }

    // ---- InputLogic ----

    /** Vietnamese subtypes without explicit combining rules use the Telex combiner. */
    @JvmStatic
    fun combiningSpecFor(combiningSpec: String?, settingsValues: SettingsValues?): String? =
        if (combiningSpec.isNullOrEmpty() && settingsValues != null && settingsValues.mLocale.language == "vi")
            COMBINING_SPEC
        else combiningSpec

    /** [beforeCodeInput] result: nothing for InputLogic to do. */
    const val ACTION_NONE = 0
    /** [beforeCodeInput] result: unlearn the composing word and reset the entire input state. */
    const val ACTION_UNLEARN_AND_RESET = 1
    /** [beforeCodeInput] result: commit the composing word without separator. */
    const val ACTION_COMMIT_TYPED = 2

    /**
     * Runs at the start of InputLogic.onCodeInput (inside the batch edit). Finishes a word picked
     * from the suggestion strip when typing continues after it, and re-seeds the word before the
     * cursor when a tone/horn key follows a finished word ("Sửa dấu từ đã gõ").
     * Returns one of the ACTION_ constants for things only InputLogic can do.
     */
    @JvmStatic
    fun beforeCodeInput(
        wordComposer: WordComposer, connection: RichInputConnection, spaceState: Int,
        settingsValues: SettingsValues, event: Event
    ): Int {
        if (!wordComposer.hasVietTelexCombiner()) return ACTION_NONE
        if (SpaceState.PHANTOM == spaceState && wordComposer.isComposingWord
            && !event.isFunctionalKeyEvent && event.codePoint >= 0
            && !settingsValues.isWordSeparator(event.codePoint)
        ) {
            return if (wordComposer.isCursorFrontOrMiddleOfComposingWord) ACTION_UNLEARN_AND_RESET
            else ACTION_COMMIT_TYPED
        }
        if (!wordComposer.isComposingWord && (VietTelexSettings.current?.reEditWords ?: true)
            && !event.isFunctionalKeyEvent && event.codePoint > 0 && event.codePoint < 128
            && !connection.hasSelection()
        ) {
            val ch = event.codePoint.toChar()
            if (!ReEdit.isTransformKey(ch, false)) return ACTION_NONE
            val before = connection.getTextBeforeCursor(64, 0) ?: return ACTION_NONE
            val after = connection.getTextAfterCursor(2, 0)
            if (!ReEdit.atWordEnd(after?.toString() ?: "")) return ACTION_NONE
            val trailing = ReEdit.trailingWord(before.toString()) ?: return ACTION_NONE
            val vt = wordComposer.vietTelexCombiner ?: return ACTION_NONE
            if (vt.canSeedAndTransform(trailing, ch)) {
                connection.deleteTextBeforeCursor(trailing.length)
                wordComposer.reset()
                if (vt.seedForTransform(trailing)) wordComposer.syncVietTelexState(true, true)
            }
        }
        return ACTION_NONE
    }

    /**
     * After a backspace when suggestions are not looked up: re-open the word that was just
     * committed so tones can be changed. Deletes it from the field and returns the text that
     * InputLogic must set as composing text, or null.
     */
    @JvmStatic
    fun reopenAfterDelete(wordComposer: WordComposer, connection: RichInputConnection): String? {
        if (!wordComposer.hasVietTelexCombiner() || !(VietTelexSettings.current?.reEditWords ?: true)
            || connection.hasSelection()
        ) return null
        val before = connection.getTextBeforeCursor(64, 0) ?: return null
        val vt = wordComposer.vietTelexCombiner ?: return null
        if (!vt.engine.canReopenLastCommit) return null
        val reopened = vt.engine.reopenLastCommit()
        if (reopened == null || !before.toString().endsWith(reopened)) {
            vt.engine.reset()
            return null
        }
        wordComposer.syncVietTelexState(true, true)
        connection.deleteTextBeforeCursor(reopened.length)
        return reopened
    }

    /** Applies auto-restore etc. to the composing word right before it is committed at a boundary. */
    @JvmStatic
    fun finishBoundary(wordComposer: WordComposer) {
        val vt = wordComposer.vietTelexCombiner ?: return
        if (!wordComposer.isComposingWord || wordComposer.isBatchMode) return
        vt.commitBoundary()
        wordComposer.syncVietTelexState(true, false)
        wordComposer.setAutoCorrection(null)
    }

    /** A separator was committed; may apply a retroactive English correction to previous words. */
    @JvmStatic
    fun noteBoundaryCharacter(wordComposer: WordComposer, codePoint: Int, connection: RichInputConnection) {
        if (codePoint < 0 || codePoint > 0xFFFF) return
        wordComposer.vietTelexCombiner?.noteBoundaryCharacter(codePoint.toChar(), connection)
    }

    /** Input restarted or the cursor moved: forget cross-word context. */
    @JvmStatic
    fun resetContext(wordComposer: WordComposer) {
        val vt = wordComposer.vietTelexCombiner ?: return
        vt.resetContext()
        wordComposer.syncVietTelexState(false, false)
    }

    // ---- Suggest ----

    /** Adds Vietnamese completions and slip corrections to [suggestionsContainer]. */
    @JvmStatic
    fun addSuggestions(
        wordComposer: WordComposer, typedWordString: String,
        suggestionsContainer: ArrayList<SuggestedWordInfo>, currentLocale: Locale
    ) {
        if (!wordComposer.isComposingWord) return
        if (currentLocale.language != "vi" && wordComposer.vietTelexRawKeystrokes == null) return
        val raw = wordComposer.vietTelexRawKeystrokes ?: typedWordString
        if (raw.isEmpty() || typedWordString.isEmpty()) return

        val pool = VNSuggest.matches(typedWordString, poolLimit = 24, excluding = typedWordString.lowercase())
        val autoFixAdjacent = VietTelexSettings.current?.autoFixAdjacent != false
        val fix = if (autoFixAdjacent && pool.isEmpty() && raw.length >= 2) {
            AdjacentKeyFixer.lexiconCorrection(raw, { VietTelexCombiner.composeTrial(it) }, false)
        } else null
        val slip = if (autoFixAdjacent && fix == null && raw.length >= 2) {
            AdjacentKeyFixer.lexiconToneSlip(raw, { VietTelexCombiner.composeTrial(it) }, false)
        } else null

        val seen = HashSet<String>(suggestionsContainer.size + 8)
        seen.add(typedWordString.lowercase())
        for (existing in suggestionsContainer) {
            seen.add(existing.word.lowercase())
        }

        val capitalizeFirst = typedWordString.firstOrNull()?.isUpperCase() == true
        fun applyCase(word: String): String {
            if (!capitalizeFirst || word.isEmpty()) return word
            return word.replaceFirstChar { it.uppercase() }
        }
        fun info(word: String, score: Int) = SuggestedWordInfo(
            word, "", score, SuggestedWordInfo.KIND_CORRECTION, Dictionary.DICTIONARY_USER_TYPED,
            SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE
        )

        var insertPos = 0
        if (fix != null) {
            val formattedFix = applyCase(fix)
            if (seen.add(formattedFix.lowercase())) {
                suggestionsContainer.add(insertPos++, info(formattedFix, SuggestedWordInfo.MAX_SCORE - 1))
            }
        }

        var score = SuggestedWordInfo.MAX_SCORE - 10
        for (candidate in pool.take(5)) {
            val formatted = applyCase(candidate.word)
            if (seen.add(formatted.lowercase())) {
                suggestionsContainer.add(insertPos++, info(formatted, score--))
            }
        }

        if (slip != null) {
            val formattedSlip = applyCase(slip)
            if (seen.add(formattedSlip.lowercase())) {
                suggestionsContainer.add(insertPos, info(formattedSlip, score))
            }
        }
    }
}
