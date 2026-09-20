package com.vernai.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.vernai.core.model.Language

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-only, 100% offline preferences manager for VernAI.
 * Persists the user's chosen language and onboarding status on this device.
 */
object UserPreferencesManager {

    private const val PREFS_NAME = "vernai_user_preferences"
    private const val KEY_PREFERRED_LANGUAGE = "preferred_language"
    private const val KEY_IS_LANGUAGE_CONFIGURED = "is_language_configured"

    private val _preferredLanguageFlow = MutableStateFlow<Language?>(null)
    val preferredLanguageFlow: StateFlow<Language?> = _preferredLanguageFlow.asStateFlow()

    internal var preferencesOverride: SharedPreferences? = null

    private fun getPrefs(context: Context?): SharedPreferences {
        preferencesOverride?.let { return it }
        return requireNotNull(context) { "Context must not be null when preferencesOverride is not set" }
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks whether the user has explicitly selected their preferred language during onboarding.
     */
    fun isLanguageConfigured(context: Context? = null): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LANGUAGE_CONFIGURED, false)
    }

    /**
     * Retrieves the user's stored preferred language. Defaults to Telugu if not configured.
     */
    fun getPreferredLanguage(context: Context? = null): Language {
        val code = getPrefs(context).getString(KEY_PREFERRED_LANGUAGE, null)
        val lang = if (code != null) {
            Language.fromBcp47(code)
        } else {
            Language.TELUGU
        }
        _preferredLanguageFlow.value = lang
        return lang
    }

    /**
     * Persists the user's preferred language and marks onboarding as completed.
     */
    fun setPreferredLanguage(context: Context? = null, language: Language) {
        getPrefs(context)
            .edit()
            .putString(KEY_PREFERRED_LANGUAGE, language.bcp47)
            .putBoolean(KEY_IS_LANGUAGE_CONFIGURED, true)
            .apply()
        _preferredLanguageFlow.value = language
    }

    /**
     * Resets preferences (useful for testing or full data wipe).
     */
    fun clearPreferences(context: Context? = null) {
        getPrefs(context)
            .edit()
            .clear()
            .apply()
        _preferredLanguageFlow.value = null
    }
}
