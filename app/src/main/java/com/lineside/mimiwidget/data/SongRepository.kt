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

    suspend fun saveSpecificSong(song: Song, isNewCycle: Boolean = false) {
        val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentMillis = System.currentTimeMillis()

        WidgetDataStore.saveTodaysSong(context, song, todayString, currentMillis, isNewCycle)

        // 【追加】手動で指定した場合も、NoRepeatがオンなら「記憶」に追加して重複を防ぎます。
        val prefs = context.dataStore.data.first()
        val noRepeat = prefs[WidgetDataStore.KEY_NO_REPEAT] ?: false
        if (noRepeat) {
            WidgetDataStore.addPlayedSong(context, song.youtubeId)
        }

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

        // 【追加】直前まで流れていた曲を把握します。
        val currentYoutubeId = prefs[WidgetDataStore.KEY_YOUTUBE_ID] ?: ""

        var availableSongs = allSongs.filter { it.youtubeId !in disabledSongs }
        if (availableSongs.isEmpty()) availableSongs = allSongs

        var cycleReset = false //新クール判定フラグを追加

        if (noRepeat) {
            var unplayedSongs = availableSongs.filter { it.youtubeId !in playedSongs }

            // もし未再生の曲がない（全曲回った）場合は記憶をリセットします。
            if (unplayedSongs.isEmpty()) {
                WidgetDataStore.clearPlayedSongs(context)
                // 【追加】リセット直後に「さっきまで流れていた曲」がいきなり選ばれるのを防ぎます。
                cycleReset = true //全曲回ったのでフラグをONにします
                unplayedSongs = availableSongs.filter { it.youtubeId != currentYoutubeId }
                // もし1曲しか設定されていないなどの理由で空になったら、全曲を対象に戻します。
                if (unplayedSongs.isEmpty()) unplayedSongs = availableSongs
            }
            availableSongs = unplayedSongs
        } else {
            // 【追加】NoRepeatがオフの時でも、全く同じ曲が2回連続で選ばれるのは不自然なので避けます。
            val withoutCurrent = availableSongs.filter { it.youtubeId != currentYoutubeId }
            if (withoutCurrent.isNotEmpty()) {
                availableSongs = withoutCurrent
            }
        }

        val randomSong = availableSongs.random()

        if (noRepeat) {
            WidgetDataStore.addPlayedSong(context, randomSong.youtubeId)
        }

        saveSpecificSong(randomSong, isNewCycle = cycleReset)
        return true
    }
}