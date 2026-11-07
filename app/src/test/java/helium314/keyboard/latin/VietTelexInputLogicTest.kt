// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.os.Message
import android.view.inputmethod.EditorInfo
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowInputMethodService.Companion.composingEnd
import helium314.keyboard.ShadowInputMethodService.Companion.composingStart
import helium314.keyboard.ShadowInputMethodService.Companion.selectionEnd
import helium314.keyboard.ShadowInputMethodService.Companion.selectionStart
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.viettelex.VietTelexSettings
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end Vietnamese Telex typing through LatinIME / InputLogic / WordComposer, using the
 * InputLogicTest shadows. This is the guard for upstream syncs: if a HeliBoard change bypasses a
 * VietTelexHooks call, these tests fail even though the engine tests still pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class VietTelexInputLogicTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val text get() = ShadowInputMethodService.text

    @BeforeTest
    fun reset() {
        ShadowInputMethodService.reset()
        latinIME.prefs().edit { clear() }
        setText("")
        // after starting input: onStartInputView would restart combining with the (shadowed) system subtype
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("vi".constructLocale()).first())
    }

    @Test fun composesTelex() {
        type("tieengs vieejt ")
        assertEquals("tiếng việt ", text)
    }

    @Test fun autoRestoresEnglishAtBoundary() {
        type("text google ")
        assertEquals("text google ", text)
    }

    @Test fun autoRestoreCanBeDisabled() {
        latinIME.prefs().edit { putBoolean(VietTelexSettings.PREF_AUTO_RESTORE, false) }
        type("user ")
        assertEquals("ủe ", text)
    }

    @Test fun keepsEnglishSentenceInContext() {
        type("his car is very fast ")
        assertEquals("his car is very fast ", text)
    }

    @Test fun reEditAfterBackspace() {
        type("anh ")
        delete()
        type("s")
        assertEquals("ánh", text)
    }

    @Test fun reEditAutoRestoredWordAfterBackspace() {
        type("vieet ")
        assertEquals("vieet ", text)
        delete()
        type("j")
        assertEquals("việt", text)
    }

    @Test fun reEditAfterCursorMoveThenMoreLetters() {
        type("tiee nay")
        setCursorPosition(3)
        type("ngs")
        assertEquals("tiếng nay", text)
    }

    @Test fun reEditDisabledKeepsLettersLiteral() {
        latinIME.prefs().edit { putBoolean(VietTelexSettings.PREF_RE_EDIT_WORDS, false) }
        type("tiee nay")
        setCursorPosition(3)
        type("ngs")
        assertEquals("tiêngs nay", text)
    }





    @Test fun typingAfterAppClearsFieldWhileComposing() {
        type("nguwo")
        assertEquals("ngưo", text)
        clearFieldExternally() // e.g. the X button of a search box
        type("dduwow")
        assertEquals("đươ", text)
    }

    // ------- helpers (same approach as InputLogicTest) ---------

    private fun type(s: String) = s.forEach {
        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(it.code))
        handleMessages()
    }

    private fun delete() {
        latinIME.onEvent(Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, KeyCode.DELETE, 0,
            Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        handleMessages()
    }

    private fun setText(newText: String) {
        ShadowInputMethodService.text = newText
        selectionStart = newText.length
        selectionEnd = selectionStart
        composingStart = -1
        composingEnd = -1
        val ei = EditorInfo()
        ei.inputType = ShadowInputMethodService.currentInputType
        latinIME.mHandler.onStartInput(ei, false)
        latinIME.mHandler.onStartInputView(ei, false)
        handleMessages()
    }

    private fun setCursorPosition(pos: Int) {
        val oldStart = selectionStart
        val oldEnd = selectionEnd
        selectionStart = pos
        selectionEnd = pos
        latinIME.onUpdateSelection(oldStart, oldEnd, pos, pos, composingStart, composingEnd)
        handleMessages()
    }

    /** The app empties the field (e.g. a search box's X button) while a word is being composed. */
    private fun clearFieldExternally() {
        val oldStart = selectionStart
        val oldEnd = selectionEnd
        ShadowInputMethodService.text = ""
        selectionStart = 0
        selectionEnd = 0
        composingStart = -1
        composingEnd = -1
        latinIME.onUpdateSelection(oldStart, oldEnd, 0, 0, -1, -1)
        handleMessages()
    }

    // ShadowHandler collects messages in private top-level lists of InputLogicTest.kt
    @Suppress("UNCHECKED_CAST")
    private fun messageList(name: String): MutableList<Message> =
        Class.forName("helium314.keyboard.latin.InputLogicTestKt").getDeclaredField(name)
            .apply { isAccessible = true }.get(null) as MutableList<Message>

    private fun handleMessages() {
        val messages = messageList("messages")
        val delayed = messageList("delayedMessages")
        fun drain() { while (messages.isNotEmpty()) latinIME.mHandler.handleMessage(messages.removeAt(0)) }
        drain()
        while (delayed.isNotEmpty()) {
            val msg = delayed.removeAt(0)
            if (msg.what != 2) latinIME.mHandler.handleMessage(msg) // skip MSG_UPDATE_SUGGESTION_STRIP like InputLogicTest
            drain()
        }
    }
}
