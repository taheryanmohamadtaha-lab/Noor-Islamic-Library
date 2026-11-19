package com.noor.library.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noor_prefs")

data class Settings(
    val theme: String = "dark",          // dark | light | cream
    val arabicSize: Float = 28f,
    val persianSize: Float = 16f,
    val lineSpacing: Float = 1.8f,
    val showTranslation: Boolean = true,
    val arabicFont: String = "amiri",    // amiri | naskh | system
    val persianFont: String = "vazir",   // vazir | system
    val ignoreHarakat: Boolean = true,
    val dailyGoal: Int = 20,
    val todayCount: Int = 0,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reciter: String = "Alafasy_128kbps",
)

data class LastPosition(val bookId: String, val sectionType: String, val sectionNumber: Int, val passage: Int)

class Prefs(private val context: Context) {
    private val ds get() = context.dataStore

    private object K {
        val theme = stringPreferencesKey("theme")
        val arabicSize = floatPreferencesKey("arabic_size")
        val persianSize = floatPreferencesKey("persian_size")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val showTranslation = booleanPreferencesKey("show_translation")
        val arabicFont = stringPreferencesKey("arabic_font")
        val persianFont = stringPreferencesKey("persian_font")
        val ignoreHarakat = booleanPreferencesKey("ignore_harakat")
        val dailyGoal = intPreferencesKey("daily_goal")
        val todayDate = stringPreferencesKey("today_date")
        val todayCount = intPreferencesKey("today_count")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reciter = stringPreferencesKey("reciter")
        val contentVersion = stringPreferencesKey("content_version")
        val lastBook = stringPreferencesKey("last_book")
        val lastType = stringPreferencesKey("last_type")
        val lastSection = intPreferencesKey("last_section")
        val lastPassage = intPreferencesKey("last_passage")
    }

    val settings: Flow<Settings> = ds.data.map { p ->
        val today = LocalDate.now().toString()
        val d = Settings()
        Settings(
            theme = p[K.theme] ?: d.theme,
            arabicSize = p[K.arabicSize] ?: d.arabicSize,
            persianSize = p[K.persianSize] ?: d.persianSize,
            lineSpacing = p[K.lineSpacing] ?: d.lineSpacing,
            showTranslation = p[K.showTranslation] ?: d.showTranslation,
            arabicFont = p[K.arabicFont] ?: d.arabicFont,
            persianFont = p[K.persianFont] ?: d.persianFont,
            ignoreHarakat = p[K.ignoreHarakat] ?: d.ignoreHarakat,
            dailyGoal = p[K.dailyGoal] ?: d.dailyGoal,
            todayCount = if (p[K.todayDate] == today) (p[K.todayCount] ?: 0) else 0,
            reminderEnabled = p[K.reminderEnabled] ?: d.reminderEnabled,
            reminderHour = p[K.reminderHour] ?: d.reminderHour,
            reciter = p[K.reciter] ?: d.reciter,
        )
    }

    val lastPosition: Flow<LastPosition?> = ds.data.map { p ->
        val b = p[K.lastBook] ?: return@map null
        LastPosition(b, p[K.lastType] ?: "section", p[K.lastSection] ?: 1, p[K.lastPassage] ?: 1)
    }

    suspend fun contentVersionNow(): String = ds.data.first()[K.contentVersion] ?: ""
    suspend fun setContentVersion(v: String) { ds.edit { it[K.contentVersion] = v } }

    suspend fun setTheme(v: String) { ds.edit { it[K.theme] = v } }
    suspend fun setArabicSize(v: Float) { ds.edit { it[K.arabicSize] = v } }
    suspend fun setPersianSize(v: Float) { ds.edit { it[K.persianSize] = v } }
    suspend fun setLineSpacing(v: Float) { ds.edit { it[K.lineSpacing] = v } }
    suspend fun setShowTranslation(v: Boolean) { ds.edit { it[K.showTranslation] = v } }
    suspend fun setArabicFont(v: String) { ds.edit { it[K.arabicFont] = v } }
    suspend fun setPersianFont(v: String) { ds.edit { it[K.persianFont] = v } }
    suspend fun setIgnoreHarakat(v: Boolean) { ds.edit { it[K.ignoreHarakat] = v } }
    suspend fun setDailyGoal(v: Int) { ds.edit { it[K.dailyGoal] = v } }
    suspend fun setReciter(v: String) { ds.edit { it[K.reciter] = v } }
    suspend fun setReminder(enabled: Boolean, hour: Int) {
        ds.edit { it[K.reminderEnabled] = enabled; it[K.reminderHour] = hour }
    }

    suspend fun savePosition(bookId: String, type: String, section: Int, passage: Int) {
        ds.edit {
            it[K.lastBook] = bookId; it[K.lastType] = type
            it[K.lastSection] = section; it[K.lastPassage] = passage
        }
    }

    /** Adds [delta] read passages to today's counter (resets automatically on a new day). */
    suspend fun addProgress(delta: Int) {
        if (delta <= 0) return
        val today = LocalDate.now().toString()
        ds.edit {
            val same = it[K.todayDate] == today
            it[K.todayDate] = today
            it[K.todayCount] = (if (same) (it[K.todayCount] ?: 0) else 0) + delta
        }
    }
}
