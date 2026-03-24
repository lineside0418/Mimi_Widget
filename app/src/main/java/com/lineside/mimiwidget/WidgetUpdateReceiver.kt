package com.lineside.mimiwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lineside.mimiwidget.data.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// アラームが鳴った時にAndroidから呼び出される受信機（レシーバー）です
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MimiWidget_LOG", "アラームを受信しました！ウィジェットの自動更新を開始します")

        // バックグラウンドで安全に通信を行うための処理です
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 曲をランダムに取得して更新・同期します
                val repository = SongRepository(context)
                repository.fetchAndSaveRandomSong()

                // 2. 更新が終わったら、次回のタイマー（アラーム）をセットし直します
                AlarmScheduler.scheduleNextUpdate(context)

            } catch (e: Exception) {
                Log.e("MimiWidget_LOG", "自動更新中にエラーが発生しました", e)
            } finally {
                // 処理の完了をAndroidに報告します
                pendingResult.finish()
            }
        }
    }
}