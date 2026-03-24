package com.lineside.mimiwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lineside.mimiwidget.data.WidgetDataStore
import com.lineside.mimiwidget.data.dataStore
import kotlinx.coroutines.flow.first
import java.util.Calendar

object AlarmScheduler {

    suspend fun scheduleNextUpdate(context: Context) {
        val prefs = context.dataStore.data.first()
        val mode = prefs[WidgetDataStore.KEY_UPDATE_MODE] ?: "daily"
        val hour = prefs[WidgetDataStore.KEY_DAILY_HOUR] ?: 0
        val min = prefs[WidgetDataStore.KEY_DAILY_MINUTE] ?: 0
        val slideMins = prefs[WidgetDataStore.KEY_SLIDESHOW_MINUTES] ?: 60
        // 前回の更新時間を読み込みます
        val lastUpdateMillis = prefs[WidgetDataStore.KEY_LAST_UPDATE_MILLIS] ?: System.currentTimeMillis()

        schedule(context, mode, hour, min, slideMins, lastUpdateMillis)
    }

    fun schedule(context: Context, mode: String, dailyHour: Int, dailyMinute: Int, slideshowMinutes: Int, lastUpdateMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = Calendar.getInstance()
        val targetTime = if (mode == "daily") {
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, dailyHour)
                set(Calendar.MINUTE, dailyMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now) || target.timeInMillis == now.timeInMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            target.timeInMillis
        } else {
            // 【バグ修正】現在時刻ではなく、前回の更新時刻を基準に次のアラームを計算します
            val expectedNext = lastUpdateMillis + (maxOf(15, slideshowMinutes) * 60 * 1000L)
            if (expectedNext <= now.timeInMillis) {
                // もし計算上の予定時刻をすでに過ぎていれば、5秒後にすぐ実行させます
                now.timeInMillis + 5000L
            } else {
                expectedNext
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pendingIntent)
            Log.d("MimiWidget_LOG", "次のアラームをセットしました: ${java.util.Date(targetTime)}")
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pendingIntent)
            Log.d("MimiWidget_LOG", "権限制限のため、代替アラームをセットしました")
        }
    }
}