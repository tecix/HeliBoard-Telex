// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.SwitchPreference

fun createVietnameseSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_VN_FREE_MARKING,
        R.string.vn_free_marking_title, R.string.vn_free_marking_summary
    ) {
        SwitchPreference(it, Defaults.PREF_VN_FREE_MARKING)
    },
    Setting(context, Settings.PREF_VN_SIMPLE_TELEX,
        R.string.vn_simple_telex_title, R.string.vn_simple_telex_summary
    ) {
        SwitchPreference(it, Defaults.PREF_VN_SIMPLE_TELEX)
    },
    Setting(context, Settings.PREF_VN_CONTEXTUAL_ENGLISH,
        R.string.vn_contextual_english_title, R.string.vn_contextual_english_summary
    ) {
        SwitchPreference(it, Defaults.PREF_VN_CONTEXTUAL_ENGLISH)
    },
    Setting(context, Settings.PREF_VN_AUTO_FIX_ADJACENT,
        R.string.vn_auto_fix_adjacent_title, R.string.vn_auto_fix_adjacent_summary
    ) {
        SwitchPreference(it, Defaults.PREF_VN_AUTO_FIX_ADJACENT)
    },
    Setting(context, Settings.PREF_VN_AUTO_RESTORE,
        R.string.vn_auto_restore_title, R.string.vn_auto_restore_summary
    ) {
        SwitchPreference(it, Defaults.PREF_VN_AUTO_RESTORE)
    },
    Setting(context, Settings.PREF_VN_LIVE_SPELL_CHECK,
        R.string.vn_live_spell_check_title, R.string.vn_live_spell_check_summary
    ) {
        SwitchPreference(it, Defaults.PREF_VN_LIVE_SPELL_CHECK)
    },
    Setting(context, Settings.PREF_VN_RE_EDIT_WORDS,
        R.string.vn_re_edit_words_title, R.string.vn_re_edit_words_summary
    ) {
        SwitchPreference(it, Defaults.PREF_VN_RE_EDIT_WORDS)
    },
)
