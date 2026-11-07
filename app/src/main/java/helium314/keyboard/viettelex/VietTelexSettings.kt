// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.viettelex

import android.content.Context
import android.content.SharedPreferences
import helium314.keyboard.latin.utils.prefs

/**
 * Vietnamese Telex preferences, kept outside HeliBoard's Settings/SettingsValues/Defaults so the
 * upstream files stay untouched. [current] is null until [install] runs (e.g. in JVM unit tests);
 * callers treat null exactly like the former "no SettingsValues" case.
 */
object VietTelexSettings {
    const val PREF_FREE_MARKING = "vn_free_marking"
    const val PREF_SIMPLE_TELEX = "vn_simple_telex"
    const val PREF_CONTEXTUAL_ENGLISH = "vn_contextual_english"
    const val PREF_AUTO_FIX_ADJACENT = "vn_auto_fix_adjacent"
    const val PREF_AUTO_RESTORE = "vn_auto_restore"
    const val PREF_LIVE_SPELL_CHECK = "vn_live_spell_check"
    const val PREF_RE_EDIT_WORDS = "vn_re_edit_words"

    /** Display order in the settings UI. */
    val ALL_KEYS = listOf(
        PREF_FREE_MARKING, PREF_SIMPLE_TELEX, PREF_CONTEXTUAL_ENGLISH, PREF_AUTO_FIX_ADJACENT,
        PREF_AUTO_RESTORE, PREF_LIVE_SPELL_CHECK, PREF_RE_EDIT_WORDS,
    )

    /** All options default to on. */
    const val DEFAULT = true

    class Values(prefs: SharedPreferences) {
        val freeMarking = prefs.getBoolean(PREF_FREE_MARKING, DEFAULT)
        val simpleTelex = prefs.getBoolean(PREF_SIMPLE_TELEX, DEFAULT)
        val contextualEnglish = prefs.getBoolean(PREF_CONTEXTUAL_ENGLISH, DEFAULT)
        val autoFixAdjacent = prefs.getBoolean(PREF_AUTO_FIX_ADJACENT, DEFAULT)
        val autoRestore = prefs.getBoolean(PREF_AUTO_RESTORE, DEFAULT)
        val liveSpellCheck = prefs.getBoolean(PREF_LIVE_SPELL_CHECK, DEFAULT)
        val reEditWords = prefs.getBoolean(PREF_RE_EDIT_WORDS, DEFAULT)
    }

    @Volatile
    @JvmStatic
    var current: Values? = null
        internal set // tests reset it

    // SharedPreferences keeps listeners in a weak map, so hold a strong reference here.
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @JvmStatic
    fun install(context: Context) {
        val prefs = context.prefs()
        current = Values(prefs)
        if (listener == null) {
            val l = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                if (key == null || key in ALL_KEYS) current = Values(sp)
            }
            listener = l
            prefs.registerOnSharedPreferenceChangeListener(l)
        }
    }
}
