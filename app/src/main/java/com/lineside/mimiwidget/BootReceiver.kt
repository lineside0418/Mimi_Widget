package com.lineside.mimiwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// スマホが再起動した時にAndroidから呼ばれる係です
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("MimiWidget_LOG", "スマホの再起動を検知しました！アラームを自動で再設定します")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 保存されている設定を読み込んで、タイマーを復活させます
                    AlarmScheduler.scheduleNextUpdate(context)
                } catch (e: Exception) {
                    Log.e("MimiWidget_LOG", "再起動時のアラーム再設定に失敗しました", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}