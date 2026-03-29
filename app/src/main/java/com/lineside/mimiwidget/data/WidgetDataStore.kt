package com.lineside.mimiwidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.TimeZone

data class UpdateHistory(val timestamp: Long, val title: String)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mimi_widget_prefs")

object WidgetDataStore {
    val KEY_TITLE = stringPreferencesKey("song_title")
    val KEY_LYRICS = stringPreferencesKey("song_lyrics")
    val KEY_YOUTUBE_ID = stringPreferencesKey("song_youtube_id")
    val KEY_LAST_UPDATE = stringPreferencesKey("last_update_date")
    val KEY_LAST_UPDATE_MILLIS = longPreferencesKey("last_update_millis")

    val KEY_UPDATE_MODE = stringPreferencesKey("update_mode")
    val KEY_DAILY_HOUR = intPreferencesKey("daily_hour")
    val KEY_DAILY_MINUTE = intPreferencesKey("daily_minute")
    val KEY_SLIDESHOW_MINUTES = intPreferencesKey("slideshow_minutes")

    val KEY_NO_REPEAT = booleanPreferencesKey("no_repeat")
    val KEY_SHOW_YEAR = booleanPreferencesKey("show_year")
    val KEY_SHOW_WEEKDAY = booleanPreferencesKey("show_weekday")
    val KEY_SHOW_REFRESH_BTN = booleanPreferencesKey("show_refresh_btn")
    val KEY_DEBUG_MODE_ENABLED = booleanPreferencesKey("debug_mode_enabled")

    // 【新規】朝活モード関連のキー
    val KEY_ASAKATSU_MODE = booleanPreferencesKey("asakatsu_mode")
    val KEY_ASAKATSU_START_MILLIS = longPreferencesKey("asakatsu_start_millis")

    val KEY_DISABLED_SONGS = stringSetPreferencesKey("disabled_songs")
    val KEY_PLAYED_SONGS = stringSetPreferencesKey("played_songs")

    val KEY_FONT_FAMILY_LYRICS = stringPreferencesKey("font_family_lyrics")
    val KEY_FONT_SIZE_LYRICS = floatPreferencesKey("font_size_lyrics")
    val KEY_FONT_FAMILY_DATE = stringPreferencesKey("font_family_date")
    val KEY_FONT_SIZE_DATE = floatPreferencesKey("font_size_date")
    val KEY_FONT_FAMILY_TITLE = stringPreferencesKey("font_family_title")
    val KEY_FONT_SIZE_TITLE = floatPreferencesKey("font_size_title")

    val KEY_CACHED_SONGS = stringPreferencesKey("cached_songs")
    val KEY_UPDATE_HISTORY = stringPreferencesKey("update_history")

    // 朝活モードでの経過日数を計算します（設定日を1日目とする）
    fun calculateAsakatsuDays(startMillis: Long, currentMillis: Long): Long {
        val start = Calendar.getInstance().apply {
            timeInMillis = startMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val current = Calendar.getInstance().apply {
            timeInMillis = currentMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return ((current - start) / (1000 * 60 * 60 * 24)) + 1
    }

    suspend fun saveTodaysSong(context: Context, song: Song, currentDate: String, currentMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TITLE] = song.title
            preferences[KEY_LYRICS] = song.lyrics ?: ""
            preferences[KEY_YOUTUBE_ID] = song.youtubeId
            preferences[KEY_LAST_UPDATE] = currentDate
            preferences[KEY_LAST_UPDATE_MILLIS] = currentMillis

            val historyJson = preferences[KEY_UPDATE_HISTORY] ?: "[]"
            val type = object : TypeToken<List<UpdateHistory>>() {}.type
            val historyList: List<UpdateHistory> = try {
                Gson().fromJson(historyJson, type) ?: emptyList()
            } catch (e: Exception) { emptyList() }

            // 【変更】履歴の最大保存数を10件から200件に拡張しました
            val newHistory = (listOf(UpdateHistory(currentMillis, song.title)) + historyList).take(200)
            preferences[KEY_UPDATE_HISTORY] = Gson().toJson(newHistory)
        }
    }

    suspend fun addPlayedSong(context: Context, youtubeId: String) {
        context.dataStore.edit { preferences ->
            val currentPlayed = preferences[KEY_PLAYED_SONGS] ?: emptySet()
            val newPlayed = currentPlayed.toMutableSet()
            newPlayed.add(youtubeId)
            preferences[KEY_PLAYED_SONGS] = newPlayed.toSet()
        }
    }

    suspend fun clearPlayedSongs(context: Context) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PLAYED_SONGS] = emptySet()
        }
    }

    suspend fun saveCachedSongs(context: Context, json: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CACHED_SONGS] = json
        }
    }
}