package com.lineside.mimiwidget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import coil.imageLoader
import coil.request.ImageRequest
import com.lineside.mimiwidget.MainActivity
import com.lineside.mimiwidget.R
import com.lineside.mimiwidget.data.SongRepository
import com.lineside.mimiwidget.data.WidgetDataStore
import com.lineside.mimiwidget.data.dataStore

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repository = SongRepository(context)
        repository.fetchAndSaveRandomSong()
        MimiWidget().update(context, glanceId)
    }
}

class MimiWidget : GlanceAppWidget() {

    companion object {
        const val TAG = "MimiWidget_LOG"
        suspend fun forceUpdate(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(MimiWidget::class.java).forEach { glanceId ->
                MimiWidget().update(context, glanceId)
            }
        }
    }

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val currentContext = LocalContext.current
            val prefs by currentContext.dataStore.data.collectAsState(initial = null)

            if (prefs == null) {
                Box(modifier = GlanceModifier.fillMaxSize().background(Color.Black)) {}
                return@provideContent
            }

            val title = prefs!![WidgetDataStore.KEY_TITLE] ?: ""
            val rawLyrics = prefs!![WidgetDataStore.KEY_LYRICS] ?: ""
            val rawDate = prefs!![WidgetDataStore.KEY_LAST_UPDATE] ?: ""
            val youtubeId = prefs!![WidgetDataStore.KEY_YOUTUBE_ID] ?: ""

            // 【新機能】データが空っぽ（初期状態やエラー時）の場合はタップを促す画面を出します
            if (youtubeId.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .clickable(actionStartActivity(Intent(currentContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✨", style = TextStyle(fontSize = 32.sp))
                        Spacer(modifier = GlanceModifier.height(8.dp))
                        Text(
                            text = "タップしてアプリを開き\n設定を完了してね！",
                            style = TextStyle(color = ColorProvider(day = Color.White, night = Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        )
                    }
                }
                return@provideContent
            }

            val showRefreshBtn = prefs!![WidgetDataStore.KEY_SHOW_REFRESH_BTN] ?: true
            val showYear = prefs!![WidgetDataStore.KEY_SHOW_YEAR] ?: false
            val showWeekday = prefs!![WidgetDataStore.KEY_SHOW_WEEKDAY] ?: false

            val fontFamLyricsStr = prefs!![WidgetDataStore.KEY_FONT_FAMILY_LYRICS] ?: "Serif"
            val fontSizeLyrics = prefs!![WidgetDataStore.KEY_FONT_SIZE_LYRICS] ?: 12f
            val fontFamDateStr = prefs!![WidgetDataStore.KEY_FONT_FAMILY_DATE] ?: "Serif"
            val fontSizeDate = prefs!![WidgetDataStore.KEY_FONT_SIZE_DATE] ?: 14f
            val fontFamTitleStr = prefs!![WidgetDataStore.KEY_FONT_FAMILY_TITLE] ?: "Serif"
            val fontSizeTitle = prefs!![WidgetDataStore.KEY_FONT_SIZE_TITLE] ?: 20f

            val displayLyrics = if (rawLyrics.isNotEmpty()) "「$rawLyrics」" else ""

            var formattedDate = rawDate
            try {
                val dateObj = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(rawDate)
                if (dateObj != null) {
                    val pattern = StringBuilder()
                    if (showYear) pattern.append("yyyy/")
                    pattern.append("M/d")
                    if (showWeekday) pattern.append(" (E)")
                    formattedDate = java.text.SimpleDateFormat(pattern.toString(), java.util.Locale.JAPAN).format(dateObj)
                }
            } catch (e: Exception) {}

            var bitmap by remember { mutableStateOf<Bitmap?>(null) }
            var isDarkImage by remember { mutableStateOf(true) }

            LaunchedEffect(youtubeId) {
                val loadedBitmap = loadBitmapWithFallback(currentContext, youtubeId)
                bitmap = loadedBitmap
                isDarkImage = isBitmapDark(loadedBitmap)
            }

            // オフライン時は強制的に白文字にします
            val textColor = if (bitmap == null || isDarkImage) Color.White else Color(0xDD000000)

            MimiWidgetContent(
                currentContext, title, displayLyrics, formattedDate, bitmap, textColor, showRefreshBtn,
                fontFamLyricsStr, fontSizeLyrics, fontFamDateStr, fontSizeDate, fontFamTitleStr, fontSizeTitle
            )
        }
    }

    private fun isBitmapDark(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return true
        return try {
            var rSum = 0L; var gSum = 0L; var bSum = 0L
            var count = 0
            for (x in 0 until bitmap.width step 10) {
                for (y in 0 until bitmap.height step 10) {
                    val pixel = bitmap.getPixel(x, y)
                    rSum += android.graphics.Color.red(pixel)
                    gSum += android.graphics.Color.green(pixel)
                    bSum += android.graphics.Color.blue(pixel)
                    count++
                }
            }
            if (count == 0) return true
            val luminance = 0.299 * (rSum / count) + 0.587 * (gSum / count) + 0.114 * (bSum / count)
            luminance < 130
        } catch (e: Exception) { true }
    }

    // 最高画質を取得し、失敗したら標準画質を再取得する賢い関数です
    private suspend fun loadBitmapWithFallback(context: Context, youtubeId: String): Bitmap? {
        if (youtubeId.isEmpty()) return null

        val maxResUrl = "https://img.youtube.com/vi/$youtubeId/maxresdefault.jpg"
        var request = ImageRequest.Builder(context).data(maxResUrl).allowHardware(false).build()
        var result = context.imageLoader.execute(request)

        // 取得に失敗した場合（SuccessResultじゃない場合）は、hqdefaultでリトライします
        if (result !is coil.request.SuccessResult) {
            Log.d(TAG, "maxresdefaultの取得に失敗したため、hqdefaultでフォールバックします")
            val hqUrl = "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"
            request = ImageRequest.Builder(context).data(hqUrl).allowHardware(false).build()
            result = context.imageLoader.execute(request)
        }

        return (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
    }
}

@Composable
fun MimiWidgetContent(
    context: Context, title: String, displayLyrics: String, date: String, bitmap: Bitmap?, textColor: Color, showRefreshBtn: Boolean,
    fontFamLyricsStr: String, fontSizeLyrics: Float, fontFamDateStr: String, fontSizeDate: Float, fontFamTitleStr: String, fontSizeTitle: Float
) {
    val size = LocalSize.current
    val colorProvider = ColorProvider(day = textColor, night = textColor)
    val fontFamLyrics = if (fontFamLyricsStr == "Serif") FontFamily.Serif else FontFamily.SansSerif
    val fontFamDate = if (fontFamDateStr == "Serif") FontFamily.Serif else FontFamily.SansSerif
    val fontFamTitle = if (fontFamTitleStr == "Serif") FontFamily.Serif else FontFamily.SansSerif

    Box(modifier = GlanceModifier.fillMaxSize()) {

        // 【新機能】画像がある時は表示、無い時（オフライン時）は素敵なプレースホルダーを表示します
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = GlanceModifier.fillMaxSize().background(Color(0xFF2C2C34)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("☁️", style = TextStyle(fontSize = 32.sp))
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Text(
                        text = "Offline Mode",
                        style = TextStyle(color = ColorProvider(day = Color.LightGray, night = Color.LightGray), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        val gradientRes = if (textColor == Color.White) R.drawable.widget_gradient_overlay else R.drawable.widget_light_overlay
        Box(modifier = GlanceModifier.fillMaxSize().background(ImageProvider(gradientRes))) {}

        Column(
            modifier = GlanceModifier.fillMaxSize().padding(16.dp).clickable(
                actionStartActivity(Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            )
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                if (size.height >= 110.dp && displayLyrics.isNotEmpty()) {
                    Text(
                        text = displayLyrics,
                        style = TextStyle(color = colorProvider, fontSize = fontSizeLyrics.sp, fontStyle = FontStyle.Italic, fontFamily = fontFamLyrics),
                        modifier = GlanceModifier.defaultWeight()
                    )
                } else {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }

                if (showRefreshBtn) {
                    Text(
                        text = "↻",
                        style = TextStyle(color = colorProvider, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()).padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = date,
                    style = TextStyle(color = colorProvider, fontSize = fontSizeDate.sp, fontWeight = FontWeight.Medium, fontFamily = fontFamDate)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = title,
                    style = TextStyle(color = colorProvider, fontSize = fontSizeTitle.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamTitle)
                )
            }
        }
    }
}