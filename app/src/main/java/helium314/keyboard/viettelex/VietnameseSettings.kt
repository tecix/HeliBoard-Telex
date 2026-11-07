// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.viettelex

import android.content.Context
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SwitchPreference
import java.util.Locale

fun createVietnameseSettings(context: Context) = listOf(
    Setting(context, VietTelexSettings.PREF_FREE_MARKING,
        R.string.vn_free_marking_title, R.string.vn_free_marking_summary
    ) {
        SwitchPreference(it, VietTelexSettings.DEFAULT)
    },
    Setting(context, VietTelexSettings.PREF_SIMPLE_TELEX,
        R.string.vn_simple_telex_title, R.string.vn_simple_telex_summary
    ) {
        SwitchPreference(it, VietTelexSettings.DEFAULT)
    },
    Setting(context, VietTelexSettings.PREF_CONTEXTUAL_ENGLISH,
        R.string.vn_contextual_english_title, R.string.vn_contextual_english_summary
    ) {
        SwitchPreference(it, VietTelexSettings.DEFAULT)
    },
    Setting(context, VietTelexSettings.PREF_AUTO_FIX_ADJACENT,
        R.string.vn_auto_fix_adjacent_title, R.string.vn_auto_fix_adjacent_summary
    ) {
        SwitchPreference(it, VietTelexSettings.DEFAULT)
    },
    Setting(context, VietTelexSettings.PREF_AUTO_RESTORE,
        R.string.vn_auto_restore_title, R.string.vn_auto_restore_summary
    ) {
        SwitchPreference(it, VietTelexSettings.DEFAULT)
    },
    Setting(context, VietTelexSettings.PREF_LIVE_SPELL_CHECK,
        R.string.vn_live_spell_check_title, R.string.vn_live_spell_check_summary
    ) {
        SwitchPreference(it, VietTelexSettings.DEFAULT)
    },
    Setting(context, VietTelexSettings.PREF_RE_EDIT_WORDS,
        R.string.vn_re_edit_words_title, R.string.vn_re_edit_words_summary
    ) {
        SwitchPreference(it, VietTelexSettings.DEFAULT)
    },
)

/** Vietnamese Telex options shown on the language (subtype) screen; nothing for other languages. */
@Composable
fun VietnameseSubtypeSettings(locale: Locale) {
    if (locale.language != "vi") return
    HorizontalDivider()
    Text(
        stringResource(R.string.settings_category_vietnamese_telex),
        style = MaterialTheme.typography.titleMedium
    )
    VietTelexSettings.ALL_KEYS.forEach { key ->
        SettingsActivity.settingsContainer[key]?.Preference()
    }
}
