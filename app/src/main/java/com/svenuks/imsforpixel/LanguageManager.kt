package com.svenuks.imsforpixel

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Data representation of a language supported by the app.
 *
 * @property code ISO language code or BCP-47 tag (e.g., "", "en", "zh"). Empty string means follow system default.
 * @property nameRes String resource for the localized language name.
 * @property nativeName Language name in its own script.
 */
data class SupportedLanguage(
    val code: String,
    @StringRes val nameRes: Int,
    val nativeName: String
)

/**
 * Manages language preferences, locale switching, and provides easy extensibility
 * for adding additional languages in the future.
 */
object LanguageManager {
    const val PREFS_NAME = "volte_settings"
    const val KEY_LANGUAGE = "app_language"

    /**
     * List of all supported languages.
     * To add support for a new language in the future:
     * 1. Add `res/values-<lang>/strings.xml` with translated strings.
     * 2. Add an entry to this list below (e.g., `SupportedLanguage("ja", R.string.lang_japanese, "日本語")`).
     * 3. (Optional) Add `<locale android:name="<lang>"/>` to `res/xml/locales_config.xml`.
     */
    val supportedLanguages = listOf(
        SupportedLanguage(
            code = "",
            nameRes = R.string.lang_system_default,
            nativeName = "Follow System / 跟随系统"
        ),
        SupportedLanguage(
            code = "en",
            nameRes = R.string.lang_english,
            nativeName = "English"
        ),
        SupportedLanguage(
            code = "zh",
            nameRes = R.string.lang_chinese,
            nativeName = "简体中文"
        )
    )

    /**
     * Gets the currently saved language code.
     * Returns empty string if set to system default.
     */
    fun getSavedLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val appLocales = localeManager?.applicationLocales
            if (appLocales != null && !appLocales.isEmpty) {
                val tag = appLocales.toLanguageTags()
                // Match prefix if needed
                for (supported in supportedLanguages) {
                    if (supported.code.isNotEmpty() && tag.startsWith(supported.code, ignoreCase = true)) {
                        return supported.code
                    }
                }
                return tag
            }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "") ?: ""
    }

    /**
     * Changes the application language and refreshes the activity.
     */
    fun setLanguage(activity: Activity, langCode: String) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = activity.getSystemService(LocaleManager::class.java)
            if (langCode.isEmpty()) {
                localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
            } else {
                localeManager?.applicationLocales = LocaleList.forLanguageTags(langCode)
            }
        } else {
            activity.recreate()
        }
    }

    /**
     * Wraps the provided context with a configuration matching the desired locale.
     * Used in [Activity.attachBaseContext].
     */
    fun applyLocale(context: Context, langCode: String): Context {
        if (langCode.isEmpty()) {
            return context
        }
        val locale = when (langCode.lowercase()) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.forLanguageTag(langCode)
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(config)
    }
}
