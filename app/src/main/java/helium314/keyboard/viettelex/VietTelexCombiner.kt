// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.viettelex

import com.viettelex.keyboard.Cp
import com.viettelex.keyboard.ReEdit
import com.viettelex.telexcore.TelexAction
import com.viettelex.telexcore.TelexEngine
import helium314.keyboard.event.Combiner
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.Settings
import java.util.ArrayList

/**
 * Vietnamese Telex combiner powered by [TelexEngine] from VietTelex.
 */
class VietTelexCombiner : Combiner {
    val engine = TelexEngine()
    private val committedPrefix = StringBuilder()
    private var pendingSeedWord: String? = null
    private var boundaryCommittedWord: String? = null
    private var isResumedLocked = false
    var cursorAtWordEnd = true

    fun syncSettings(vs: VietTelexSettings.Values? = VietTelexSettings.current) {
        if (vs == null) return
        engine.freeMarking = vs.freeMarking
        engine.simpleTelex = vs.simpleTelex
        engine.contextualEnglish = vs.contextualEnglish
        engine.retroactiveEnglish = vs.contextualEnglish
        engine.liveSpellCheck = vs.liveSpellCheck
        engine.eagerEnglishRestore = vs.autoRestore
    }

    val isEmpty: Boolean
        get() = engine.isEmpty && committedPrefix.isEmpty() && pendingSeedWord == null && boundaryCommittedWord == null

    val rawKeystrokes: String?
        get() {
            if (!engine.isEmpty) {
                return if (committedPrefix.isEmpty()) engine.rawKeystrokes
                else committedPrefix.toString() + engine.rawKeystrokes
            }
            return pendingSeedWord
        }

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        val sv = Settings.getValues()
        val vs = VietTelexSettings.current
        syncSettings(vs)

        if (event.keyCode == KeyCode.DELETE) {
            if (!cursorAtWordEnd) return event
            boundaryCommittedWord = null
            pendingSeedWord?.let { w ->
                pendingSeedWord = null
                val dropped = Cp.dropLast(w, 1)
                if (dropped.isNotEmpty()) committedPrefix.append(dropped)
                return Event.createConsumedEvent(event)
            }
            if (!engine.isEmpty) {
                engine.backspace()
                return Event.createConsumedEvent(event)
            }
            if (committedPrefix.isNotEmpty()) {
                val dropped = Cp.dropLast(committedPrefix.toString(), 1)
                committedPrefix.setLength(0)
                committedPrefix.append(dropped)
                if (committedPrefix.isEmpty()) isResumedLocked = false
                return Event.createConsumedEvent(event)
            }
            return event
        }

        if (event.isFunctionalKeyEvent || event.isGesture || event.isSuggestionStripPress) {
            return event
        }

        val codePoint = event.codePoint
        if (codePoint < 0) return event

        if (sv != null) {
            if (sv.mInputAttributes.mIsPasswordField || sv.mInputAttributes.isTypeNull()) {
                return event
            }
            val isSeparator = Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()
                || (Character.getType(codePoint) == Character.UNASSIGNED.toInt() && StringUtils.mightBeEmoji(codePoint))
                || sv.isWordSeparator(codePoint)
            if (isSeparator) {
                return event
            }
            if (!cursorAtWordEnd) {
                return event
            }
            if (isEmpty && !sv.isWordCodePoint(codePoint)) {
                return event
            }
            if (isEmpty && sv.mSpacingAndPunctuations.isWordConnector(codePoint)) {
                if (codePoint <= 0xFFFF) engine.noteBoundaryCharacter(codePoint.toChar())
                return event
            }
        } else {
            if (Character.isWhitespace(codePoint)) return event
        }

        boundaryCommittedWord = null

        if (isResumedLocked) {
            committedPrefix.appendCodePoint(codePoint)
            return Event.createConsumedEvent(event)
        }

        pendingSeedWord?.let { w ->
            pendingSeedWord = null
            if (codePoint in 'a'.code..'z'.code || codePoint in 'A'.code..'Z'.code) {
                val ch = codePoint.toChar()
                if (engine.seed(w)) {
                    val action = engine.feed(ch)
                    if (isReEditKey(ch) && action is TelexAction.Replace && action.backspaces > 0 && engine.composed != w + ch) {
                        return Event.createConsumedEvent(event)
                    }
                    // a plain letter extends the word ("tiê" + n): keep it in the engine so later tone keys apply
                    if (engine.composed == w + ch) return Event.createConsumedEvent(event)
                    engine.reset()
                } else if (isReEditKey(ch) && replayRawWord(engine, w, ch)) {
                    return Event.createConsumedEvent(event)
                }
            }
            committedPrefix.append(w)
            if (codePoint in 'a'.code..'z'.code || codePoint in 'A'.code..'Z'.code) {
                engine.feed(codePoint.toChar())
            } else {
                if (codePoint <= 0xFFFF) engine.noteBoundaryCharacter(codePoint.toChar())
                committedPrefix.appendCodePoint(codePoint)
            }
            return Event.createConsumedEvent(event)
        }

        if (codePoint in 'a'.code..'z'.code || codePoint in 'A'.code..'Z'.code) {
            engine.feed(codePoint.toChar())
            return Event.createConsumedEvent(event)
        }

        if (!engine.isEmpty) {
            committedPrefix.append(engine.commitText(vs?.autoRestore ?: true))
        }
        if (codePoint <= 0xFFFF) {
            engine.noteBoundaryCharacter(codePoint.toChar())
        }
        committedPrefix.appendCodePoint(codePoint)
        return Event.createConsumedEvent(event)
    }

    /**
     * Finalize the current composing word at a word boundary (space, punctuation, enter),
     * applying auto-restore and updating cross-word English context and reopen state.
     */
    fun commitBoundary(): String {
        val vs = VietTelexSettings.current
        syncSettings(vs)
        boundaryCommittedWord?.let { return it }
        pendingSeedWord?.let { w ->
            pendingSeedWord = null
            boundaryCommittedWord = w
            return w
        }
        val autoRestore = vs?.autoRestore ?: true
        val tail = if (!engine.isEmpty) engine.commitText(autoRestore) else ""
        val result = if (committedPrefix.isEmpty()) tail else committedPrefix.toString() + tail
        committedPrefix.setLength(0)
        isResumedLocked = false
        boundaryCommittedWord = result
        return result
    }

    /**
     * Notify the engine of a boundary character committed after a word, and apply any
     * retroactive cross-word English correction when contextualEnglish + autoRestore are enabled.
     */
    fun noteBoundaryCharacter(ch: Char, connection: RichInputConnection? = null) {
        val vs = VietTelexSettings.current
        syncSettings(vs)
        engine.noteBoundaryCharacter(ch)
        val retro = engine.pendingRetroCorrection
        if (retro != null && !retro.flipBack && (vs?.contextualEnglish ?: true) && (vs?.autoRestore ?: true)
            && ch != '\n' && connection != null && !connection.hasSelection()
        ) {
            val before = connection.getTextBeforeCursor(retro.expected.length + 4, 0)?.toString()
            if (before != null && before.endsWith(retro.expected)) {
                engine.takeRetroCorrection()
                connection.deleteTextBeforeCursor(retro.expected.length)
                connection.commitText(retro.insert, 1)
            }
        }
    }

    /**
     * Resume composition on an already-typed [word] (e.g. when backspacing a boundary or touching a word).
     */
    fun resumeWord(word: String) {
        val vs = VietTelexSettings.current
        syncSettings(vs)
        committedPrefix.setLength(0)
        pendingSeedWord = null
        boundaryCommittedWord = null
        isResumedLocked = false
        cursorAtWordEnd = true
        if (word.isEmpty()) return

        val reEdit = vs?.reEditWords ?: true
        if (!reEdit) {
            if (!engine.isEmpty) engine.commitText(false)
            engine.forgetLastCommit()
            committedPrefix.append(word)
            isResumedLocked = true
            return
        }

        if (engine.canReopenLastCommit) {
            val reopened = engine.reopenLastCommit()
            if (reopened == word) {
                return
            }
            engine.reset()
        }
        pendingSeedWord = word
    }

    /**
     * Test whether [word] at the cursor can be seeded and transformed by [ch] when not currently composing.
     */
    fun canSeedAndTransform(word: String, ch: Char): Boolean {
        val vs = VietTelexSettings.current
        if (!(vs?.reEditWords ?: true)) return false
        if (!ReEdit.isTransformKey(ch, false)) return false
        val trial = createTrialEngine(vs)
        if (!trial.seed(word)) return replayRawWord(trial, word, ch)
        val action = trial.feed(ch)
        return action is TelexAction.Replace && action.backspaces > 0 && trial.composed != word + ch
    }

    /**
     * Seed [word] into the engine so the immediately following [processEvent] transforms it.
     */
    fun seedForTransform(word: String): Boolean {
        syncSettings()
        committedPrefix.setLength(0)
        pendingSeedWord = null
        boundaryCommittedWord = null
        isResumedLocked = false
        cursorAtWordEnd = true
        if (engine.seed(word)) return true
        // word left as raw keys by auto-restore ("vieet"): canSeedAndTransform already checked the next key
        if (!isRawAsciiWord(word)) return false
        engine.reset()
        for (c in word) engine.feed(c)
        return true
    }

    override val combiningStateFeedback: CharSequence
        get() {
            boundaryCommittedWord?.let { return it }
            pendingSeedWord?.let { return it }
            return if (committedPrefix.isEmpty()) engine.composed
            else committedPrefix.toString() + engine.composed
        }

    override fun reset() {
        // Only clear the in-progress word; keep cross-word English context and reopen snapshot
        // when reset() is called at the end of committing a word.
        if (!engine.isEmpty) {
            engine.commitText(false)
            engine.forgetLastCommit()
        }
        committedPrefix.setLength(0)
        pendingSeedWord = null
        boundaryCommittedWord = null
        isResumedLocked = false
        cursorAtWordEnd = true
    }

    fun resetContext() {
        engine.reset()
        engine.resetContext()
        committedPrefix.setLength(0)
        pendingSeedWord = null
        boundaryCommittedWord = null
        isResumedLocked = false
        cursorAtWordEnd = true
    }

    companion object {
        /** Keys that may transform an already typed word: tone/horn keys and the doubling vowels / d. */
        private fun isReEditKey(ch: Char) = ReEdit.isTransformKey(ch, false) || ch.lowercaseChar() in "aeod"

        private fun isRawAsciiWord(word: String) =
            word.isNotEmpty() && word.length <= ReEdit.MAX_WORD && word.all { it in 'a'..'z' || it in 'A'..'Z' }

        /**
         * A word that auto-restore left as its raw keys ("vieet", since "viêt" is not a valid syllable)
         * cannot be [TelexEngine.seed]ed, because seeding needs an exact round trip. Replay its raw keys
         * plus [ch] instead, and accept only a valid Vietnamese result, i.e. one the boundary would not
         * restore again — so English words ("google" + s) stay literal. On failure [e] is reset.
         */
        private fun replayRawWord(e: TelexEngine, word: String, ch: Char): Boolean {
            if (!isRawAsciiWord(word)) return false
            e.reset()
            for (c in word) e.feed(c)
            e.feed(ch)
            val composed = e.composed
            if (composed != word + ch && e.peekCommitText(VietTelexSettings.current?.autoRestore ?: true) == composed) return true
            e.reset()
            return false
        }

        fun createTrialEngine(vs: VietTelexSettings.Values? = VietTelexSettings.current): TelexEngine =
            TelexEngine().apply {
                if (vs != null) {
                    freeMarking = vs.freeMarking
                    simpleTelex = vs.simpleTelex
                    liveSpellCheck = vs.liveSpellCheck
                    eagerEnglishRestore = vs.autoRestore
                    contextualEnglish = false
                }
            }

        @JvmStatic
        fun composeTrial(raw: String): String {
            val t = createTrialEngine()
            for (ch in raw) t.feed(ch)
            return t.composed
        }
    }
}
