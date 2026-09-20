package com.vernai.core.preferences

import android.content.SharedPreferences
import com.vernai.core.model.Language
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data
    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = (data[key] as? Set<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(data)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val staging = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
            if (key != null) staging[key] = values
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) staging.remove(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearRequested) {
                data.clear()
            }
            data.putAll(staging)
        }
    }
}

class UserPreferencesManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        UserPreferencesManager.preferencesOverride = fakePrefs
        UserPreferencesManager.clearPreferences()
    }

    @After
    fun tearDown() {
        UserPreferencesManager.clearPreferences()
        UserPreferencesManager.preferencesOverride = null
    }

    @Test
    fun isLanguageConfigured_initiallyFalse() {
        assertFalse(
            "User language should not be configured prior to first-launch onboarding",
            UserPreferencesManager.isLanguageConfigured()
        )
    }

    @Test
    fun getPreferredLanguage_defaultsToTeluguWhenNotConfigured() {
        val defaultLang = UserPreferencesManager.getPreferredLanguage()
        assertEquals(Language.TELUGU, defaultLang)
    }

    @Test
    fun setPreferredLanguage_persistsChosenLanguageAndMarksConfigured() {
        // User selects Tamil during first launch onboarding
        UserPreferencesManager.setPreferredLanguage(language = Language.TAMIL)

        assertTrue(
            "isLanguageConfigured should be true after user confirms onboarding",
            UserPreferencesManager.isLanguageConfigured()
        )
        assertEquals(
            Language.TAMIL,
            UserPreferencesManager.getPreferredLanguage()
        )
    }

    @Test
    fun setPreferredLanguage_supportsHindiAndEnglish() {
        // User selects Hindi
        UserPreferencesManager.setPreferredLanguage(language = Language.HINDI)
        assertTrue(UserPreferencesManager.isLanguageConfigured())
        assertEquals(Language.HINDI, UserPreferencesManager.getPreferredLanguage())

        // User changes to English in Settings later
        UserPreferencesManager.setPreferredLanguage(language = Language.ENGLISH)
        assertEquals(Language.ENGLISH, UserPreferencesManager.getPreferredLanguage())
    }

    @Test
    fun preferredLanguageFlow_emitsLatestSelection() = runTest {
        UserPreferencesManager.setPreferredLanguage(language = Language.TAMIL)
        val flowValue = UserPreferencesManager.preferredLanguageFlow.first()
        assertEquals(Language.TAMIL, flowValue)

        UserPreferencesManager.setPreferredLanguage(language = Language.HINDI)
        assertEquals(Language.HINDI, UserPreferencesManager.preferredLanguageFlow.value)
    }

    @Test
    fun clearPreferences_resetsConfiguredState() {
        UserPreferencesManager.setPreferredLanguage(language = Language.TELUGU)
        assertTrue(UserPreferencesManager.isLanguageConfigured())

        UserPreferencesManager.clearPreferences()
        assertFalse(
            "Should revert to unconfigured after preferences are wiped",
            UserPreferencesManager.isLanguageConfigured()
        )
    }
}
