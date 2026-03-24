package com.lineside.mimiwidget.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lineside.mimiwidget.Constants
import com.lineside.mimiwidget.widget.MimiWidget
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 通信（API）と保存（DataStore）を繋ぐ、ロジックの司令塔クラスです。
class SongRepository(private val context: Context) {

    suspend fun fetchAllSongs(): List<Song>? {
        return try {
            val response = ApiClient.microCmsApi.getSongs(apiKey = Constants.MICROCMS_API_KEY)
            val json = Gson().toJson(response.contents)
            WidgetDataStore.saveCachedSongs(context, json)
            response.contents
        } catch (e: Exception) {
            Log.e("SongRepository", "通信エラー発生。オフラインキャッシュから読み込みを試みます", e)
            val prefs = context.dataStore.data.first()
            val cachedJson = prefs[WidgetDataStore.KEY_CACHED_SONGS]

            if (!cachedJson.isNullOrEmpty()) {
                val type = object : TypeToken<List<Song>>() {}.type
                Gson().fromJson<List<Song>>(cachedJson, type)
            } else {
                null
            }
        }
    }

    suspend fun saveSpecificSong(song: Song) {
        val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        // 【追加】現在の正確な時間を取得します
        val currentMillis = System.currentTimeMillis()

        WidgetDataStore.saveTodaysSong(context, song, todayString, currentMillis)

        try {
            MimiWidget.forceUpdate(context)
            Log.d("SongRepository", "曲の保存が完了し、ウィジェットの同期更新を行いました: ${song.title}")
        } catch (e: Exception) {
            Log.e("SongRepository", "ウィジェットの同期更新に失敗しました", e)
        }
    }

    suspend fun fetchAndSaveRandomSong(): Boolean {
        val allSongs = fetchAllSongs()
        if (allSongs.isNullOrEmpty()) return false

        val prefs = context.dataStore.data.first()
        val disabledSongs = prefs[WidgetDataStore.KEY_DISABLED_SONGS] ?: emptySet()
        val playedSongs = prefs[WidgetDataStore.KEY_PLAYED_SONGS] ?: emptySet()
        val noRepeat = prefs[WidgetDataStore.KEY_NO_REPEAT] ?: false

        var availableSongs = allSongs.filter { it.youtubeId !in disabledSongs }
        if (availableSongs.isEmpty()) availableSongs = allSongs

        if (noRepeat) {
            var unplayedSongs = availableSongs.filter { it.youtubeId !in playedSongs }
            if (unplayedSongs.isEmpty()) {
                WidgetDataStore.clearPlayedSongs(context)
                unplayedSongs = availableSongs
            }
            availableSongs = unplayedSongs
        }

        val randomSong = availableSongs.random()

        if (noRepeat) {
            WidgetDataStore.addPlayedSong(context, randomSong.youtubeId)
        }

        saveSpecificSong(randomSong)
        return true
    }
}