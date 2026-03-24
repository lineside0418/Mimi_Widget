package com.lineside.mimiwidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 履歴を保存するためのデータ型です
data class UpdateHistory(val timestamp: Long, val title: String)

// アプリ内のどこからでもアクセスできるDataStoreです。
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

    val KEY_DISABLED_SONGS = stringSetPreferencesKey("disabled_songs")
    val KEY_PLAYED_SONGS = stringSetPreferencesKey("played_songs")

    val KEY_FONT_FAMILY_LYRICS = stringPreferencesKey("font_family_lyrics")
    val KEY_FONT_SIZE_LYRICS = floatPreferencesKey("font_size_lyrics")
    val KEY_FONT_FAMILY_DATE = stringPreferencesKey("font_family_date")
    val KEY_FONT_SIZE_DATE = floatPreferencesKey("font_size_date")
    val KEY_FONT_FAMILY_TITLE = stringPreferencesKey("font_family_title")
    val KEY_FONT_SIZE_TITLE = floatPreferencesKey("font_size_title")

    val KEY_CACHED_SONGS = stringPreferencesKey("cached_songs")

    // 【追加】更新履歴を保存するためのキー
    val KEY_UPDATE_HISTORY = stringPreferencesKey("update_history")

    suspend fun saveTodaysSong(context: Context, song: Song, currentDate: String, currentMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TITLE] = song.title
            preferences[KEY_LYRICS] = song.lyrics ?: ""
            preferences[KEY_YOUTUBE_ID] = song.youtubeId
            preferences[KEY_LAST_UPDATE] = currentDate
            preferences[KEY_LAST_UPDATE_MILLIS] = currentMillis

            // 履歴を読み込んで新しいものを追加し、最新10件だけを残して保存します
            val historyJson = preferences[KEY_UPDATE_HISTORY] ?: "[]"
            val type = object : TypeToken<List<UpdateHistory>>() {}.type
            val historyList: List<UpdateHistory> = try {
                Gson().fromJson(historyJson, type) ?: emptyList()
            } catch (e: Exception) { emptyList() }

            val newHistory = (listOf(UpdateHistory(currentMillis, song.title)) + historyList).take(10)
            preferences[KEY_UPDATE_HISTORY] = Gson().toJson(newHistory)
        }
    }

    suspend fun addPlayedSong(context: Context, youtubeId: String) {
        context.dataStore.edit { preferences ->
            val currentPlayed = preferences[KEY_PLAYED_SONGS] ?: emptySet()
            preferences[KEY_PLAYED_SONGS] = currentPlayed + youtubeId
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