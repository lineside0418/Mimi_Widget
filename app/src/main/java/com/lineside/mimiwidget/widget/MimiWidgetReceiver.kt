package com.lineside.mimiwidget.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// Androidシステムからのウィジェット更新などのイベントを受け取るクラスです。
class MimiWidgetReceiver : GlanceAppWidgetReceiver() {
    // 表示するウィジェットの本体を指定します。
    override val glanceAppWidget: GlanceAppWidget = MimiWidget()
}