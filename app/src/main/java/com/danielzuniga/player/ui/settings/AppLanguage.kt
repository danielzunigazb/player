package com.danielzuniga.player.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.danielzuniga.player.R

/**
 * The app's own language, independent of the phone's. Stored by Android on 13+ (and shown in
 * Settings → Apps → Player → Language) and by AppCompat on older versions; changing it
 * recreates the activity in the new language.
 */
enum class AppLanguage(val tag: String, val label: Int) {
    SYSTEM("", R.string.language_system),
    SPANISH("es", R.string.language_es),
    ENGLISH("en", R.string.language_en);

    companion object {
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            val language = if (locales.isEmpty) "" else locales[0]?.language.orEmpty()
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }

        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (language == SYSTEM) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language.tag),
            )
        }
    }
}
