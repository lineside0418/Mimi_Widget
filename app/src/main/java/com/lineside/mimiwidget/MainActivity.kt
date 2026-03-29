package com.lineside.mimiwidget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lineside.mimiwidget.data.Song
import com.lineside.mimiwidget.data.SongRepository
import com.lineside.mimiwidget.data.UpdateHistory
import com.lineside.mimiwidget.data.WidgetDataStore
import com.lineside.mimiwidget.data.dataStore
import com.lineside.mimiwidget.widget.MimiWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun getAppVersionName(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

suspend fun fetchLatestGithubRelease(): Pair<String, String>? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/repos/lineside0418/Mimi_Widget/releases/latest")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            val tagName = jsonObject.optString("tag_name", "").replace("v", "")
            val htmlUrl = jsonObject.optString("html_url", "")
            if (tagName.isNotEmpty() && htmlUrl.isNotEmpty()) {
                return@withContext Pair(tagName, htmlUrl)
            }
        }
    } catch (e: Exception) {
        Log.e("GithubRelease", "Failed to fetch release", e)
    }
    return@withContext null
}

fun isNewerVersion(latest: String, current: String): Boolean {
    val lParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
    val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val length = maxOf(lParts.size, cParts.size)
    for (i in 0 until length) {
        val l = lParts.getOrElse(i) { 0 }
        val c = cParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            val currentYoutubeId = prefs[WidgetDataStore.KEY_YOUTUBE_ID] ?: ""
            if (currentYoutubeId.isEmpty()) {
                SongRepository(this@MainActivity).fetchAndSaveRandomSong()
            }
            AlarmScheduler.scheduleNextUpdate(this@MainActivity)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF121212), surfaceVariant = Color(0xFF1E1E1E))) {
                MainScreen()
            }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun YoutubeThumbnail(youtubeId: String, modifier: Modifier = Modifier) {
    var useHqDefault by remember(youtubeId) { mutableStateOf(false) }
    val url = if (useHqDefault) {
        "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"
    } else {
        "https://img.youtube.com/vi/$youtubeId/maxresdefault.jpg"
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
        contentDescription = "Thumbnail",
        contentScale = ContentScale.Crop,
        modifier = modifier,
        onError = {
            if (!useHqDefault) useHqDefault = true
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs by context.dataStore.data.collectAsState(initial = null)
    val isDebugEnabled = prefs?.get(WidgetDataStore.KEY_DEBUG_MODE_ENABLED) ?: false

    var selectedTab by remember { mutableStateOf(0) }
    data class TabItem(val title: String, val icon: ImageVector)

    val tabs = mutableListOf(
        TabItem("Home", Icons.Rounded.Home),
        TabItem("一般", Icons.Rounded.Settings),
        TabItem("外観", Icons.Rounded.Create),
        TabItem("曲", Icons.Rounded.List)
    )
    if (isDebugEnabled) tabs.add(TabItem("デバッグ", Icons.Rounded.Build))
    tabs.add(TabItem("情報", Icons.Rounded.Info))

    if (selectedTab >= tabs.size) selectedTab = 0

    val currentVersion = remember { getAppVersionName(context) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var releaseUrl by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val releaseInfo = fetchLatestGithubRelease()
        if (releaseInfo != null) {
            latestVersion = releaseInfo.first
            releaseUrl = releaseInfo.second
            if (isNewerVersion(releaseInfo.first, currentVersion)) {
                showUpdateDialog = true
            }
        }
    }

    if (showUpdateDialog && releaseUrl != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("アップデートのお知らせ", fontWeight = FontWeight.Bold) },
            text = { Text("新しいバージョン (v$latestVersion) がリリースされています！\nGitHubのページを開いて更新しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                    } catch (e: Exception) {
                        Toast.makeText(context, "ブラウザを開けませんでした", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("更新する", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("あとで", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[selectedTab].title, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 10.sp) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = Color.Transparent)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (tabs[selectedTab].title) {
                "Home" -> DashboardScreen()
                "一般" -> GeneralSettingsScreen()
                "外観" -> AppearanceSettingsScreen()
                "曲" -> SongsSettingsScreen()
                "デバッグ" -> DebugScreen(currentVersion, latestVersion)
                "情報" -> CreditsScreen(currentVersion)
            }
        }
    }
}

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val prefs by context.dataStore.data.collectAsState(initial = null)

    val title = prefs?.get(WidgetDataStore.KEY_TITLE) ?: "No Song"
    val rawLyrics = prefs?.get(WidgetDataStore.KEY_LYRICS) ?: "ウィジェットを追加するか、設定から曲を更新してください"
    val youtubeId = prefs?.get(WidgetDataStore.KEY_YOUTUBE_ID) ?: ""
    val displayLyrics = if (rawLyrics.isNotEmpty() && youtubeId.isNotEmpty()) "「$rawLyrics」" else rawLyrics

    val fontFamLyricsStr = prefs?.get(WidgetDataStore.KEY_FONT_FAMILY_LYRICS) ?: "Serif"
    val fontFamTitleStr = prefs?.get(WidgetDataStore.KEY_FONT_FAMILY_TITLE) ?: "Serif"

    val customMincho = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.mincho))
    val customGothic = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.gothic))

    val fontFamLyrics = if (fontFamLyricsStr == "Serif") customMincho else customGothic
    val fontFamTitle = if (fontFamTitleStr == "Serif") customMincho else customGothic

    val lastUpdateMillis = prefs?.get(WidgetDataStore.KEY_LAST_UPDATE_MILLIS) ?: System.currentTimeMillis()
    val mode = prefs?.get(WidgetDataStore.KEY_UPDATE_MODE) ?: "daily"
    val dailyHour = prefs?.get(WidgetDataStore.KEY_DAILY_HOUR) ?: 0
    val dailyMinute = prefs?.get(WidgetDataStore.KEY_DAILY_MINUTE) ?: 0
    val slideshowMins = prefs?.get(WidgetDataStore.KEY_SLIDESHOW_MINUTES) ?: 60

    // 朝活設定の読み込み
    val isAsakatsu = prefs?.get(WidgetDataStore.KEY_ASAKATSU_MODE) ?: false
    val startMillis = prefs?.get(WidgetDataStore.KEY_ASAKATSU_START_MILLIS) ?: System.currentTimeMillis()
    val dayCount = WidgetDataStore.calculateAsakatsuDays(startMillis, System.currentTimeMillis())

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val diffMillis = if (mode == "slideshow") {
        (lastUpdateMillis + (slideshowMins * 60 * 1000L)) - currentTime
    } else {
        val target = Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, dailyHour)
            set(Calendar.MINUTE, dailyMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= currentTime) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        target.timeInMillis - currentTime
    }

    val diffSecsTotal = diffMillis / 1000
    val diffHours = diffSecsTotal / 3600
    val diffMins = (diffSecsTotal % 3600) / 60
    val diffSecs = diffSecsTotal % 60

    val timeText = if (diffMillis <= 0) {
        "↻ まもなく更新されます..."
    } else if (diffHours > 0) {
        "次回更新予定: あと ${diffHours}時間 ${diffMins}分 ${diffSecs}秒"
    } else {
        "次回更新予定: あと ${diffMins}分 ${diffSecs}秒"
    }

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        YoutubeThumbnail(
            youtubeId = youtubeId,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(24.dp))
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = title, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = fontFamTitle, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = displayLyrics, fontSize = 16.sp, color = Color.LightGray, fontFamily = fontFamLyrics, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { if (youtubeId.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeId"))) },
                modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                Spacer(modifier = Modifier.width(8.dp))
                Text("YouTubeで聴く", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = {
                    if (youtubeId.isNotEmpty()) {
                        // 【変更】朝活のON/OFFに応じて、シェアテキストのフォーマットを切り替えます
                        val shareText = if (isAsakatsu) {
                            "MI民朝活${dayCount}日目\n今日の曲は「$title」！\n\n『$rawLyrics』\n\nhttps://www.youtube.com/watch?v=$youtubeId\n#MIMI_Widget"
                        } else {
                            "今日の曲は「$title」！\n\n『$rawLyrics』\n\nhttps://www.youtube.com/watch?v=$youtubeId\n#MIMI_Widget"
                        }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }, "シェアする"))
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = "Share")
                Spacer(modifier = Modifier.width(8.dp))
                Text("シェア", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = timeText, fontSize = 12.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs by context.dataStore.data.collectAsState(initial = null)

    val mode = prefs?.get(WidgetDataStore.KEY_UPDATE_MODE) ?: "daily"
    val dailyHour = prefs?.get(WidgetDataStore.KEY_DAILY_HOUR) ?: 0
    val dailyMinute = prefs?.get(WidgetDataStore.KEY_DAILY_MINUTE) ?: 0
    val slideshowMins = prefs?.get(WidgetDataStore.KEY_SLIDESHOW_MINUTES) ?: 60

    val showRefreshBtn = prefs?.get(WidgetDataStore.KEY_SHOW_REFRESH_BTN) ?: true
    val showYear = prefs?.get(WidgetDataStore.KEY_SHOW_YEAR) ?: false
    val showWeekday = prefs?.get(WidgetDataStore.KEY_SHOW_WEEKDAY) ?: false
    val noRepeat = prefs?.get(WidgetDataStore.KEY_NO_REPEAT) ?: false
    val debugMode = prefs?.get(WidgetDataStore.KEY_DEBUG_MODE_ENABLED) ?: false

    // 朝活モードの状態
    val isAsakatsu = prefs?.get(WidgetDataStore.KEY_ASAKATSU_MODE) ?: false
    val asakatsuStartMillis = prefs?.get(WidgetDataStore.KEY_ASAKATSU_START_MILLIS) ?: System.currentTimeMillis()

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = asakatsuStartMillis)

    val scrollState = rememberScrollState()
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBatteryOpt = pm.isIgnoringBatteryOptimizations(context.packageName)

    // カレンダーUIの表示ダイアログ
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        // UTCのミリ秒から、ローカルの0時0分（深夜）のミリ秒へ変換して正確に保存します
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        calendar.timeInMillis = utcMillis
                        val y = calendar.get(Calendar.YEAR)
                        val m = calendar.get(Calendar.MONTH)
                        val d = calendar.get(Calendar.DAY_OF_MONTH)
                        val localMidnight = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

                        coroutineScope.launch {
                            context.dataStore.edit { it[WidgetDataStore.KEY_ASAKATSU_START_MILLIS] = localMidnight }
                            MimiWidget.forceUpdate(context)
                        }
                    }
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル", color = Color.Gray) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 32.dp)) {

        Text("更新モード", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_UPDATE_MODE] = "daily" }; AlarmScheduler.scheduleNextUpdate(context) } },
                    colors = ButtonDefaults.buttonColors(containerColor = if (mode == "daily") MaterialTheme.colorScheme.primary else Color.DarkGray),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) { Text("デイリー") }

                Button(
                    onClick = { coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_UPDATE_MODE] = "slideshow" }; AlarmScheduler.scheduleNextUpdate(context) } },
                    colors = ButtonDefaults.buttonColors(containerColor = if (mode == "slideshow") MaterialTheme.colorScheme.primary else Color.DarkGray),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) { Text("スライドショー") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (mode == "daily") {
                Text("毎日決まった時刻に更新します", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("更新時刻:", fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                    Text("${dailyHour}時", modifier = Modifier.width(40.dp))
                    Slider(
                        value = dailyHour.toFloat(), onValueChange = { h -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_DAILY_HOUR] = h.toInt() } } },
                        onValueChangeFinished = { coroutineScope.launch { AlarmScheduler.scheduleNextUpdate(context) } },
                        valueRange = 0f..23f, steps = 22, modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(80.dp))
                    Text("${dailyMinute}分", modifier = Modifier.width(40.dp))
                    Slider(
                        value = dailyMinute.toFloat(), onValueChange = { m -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_DAILY_MINUTE] = m.toInt() } } },
                        onValueChangeFinished = { coroutineScope.launch { AlarmScheduler.scheduleNextUpdate(context) } },
                        valueRange = 0f..59f, steps = 58, modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text("一定の時間間隔で更新します（最短15分）", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                val displayTime = if (slideshowMins >= 60) "${slideshowMins / 60}時間${if (slideshowMins % 60 != 0) "${slideshowMins % 60}分" else ""}ごと" else "${slideshowMins}分ごと"
                Text(displayTime, fontWeight = FontWeight.Bold)
                Slider(
                    value = slideshowMins.toFloat(), onValueChange = { m -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_SLIDESHOW_MINUTES] = m.toInt() } } },
                    onValueChangeFinished = { coroutineScope.launch { AlarmScheduler.scheduleNextUpdate(context) } },
                    valueRange = 15f..360f, steps = 68
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("一度出た曲を重複させない", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = noRepeat, onCheckedChange = { isChecked -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_NO_REPEAT] = isChecked }; if (isChecked) WidgetDataStore.clearPlayedSongs(context) } })
            }
        }

        // 【新規】朝活モードの設定セクション
        Text("朝活", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("朝活モードを有効にする", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = isAsakatsu, onCheckedChange = { isChecked -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_ASAKATSU_MODE] = isChecked }; MimiWidget.forceUpdate(context) } })
            }
            if (isAsakatsu) {
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showDatePicker = true }.padding(vertical = 4.dp)) {
                    val dateStr = SimpleDateFormat("yyyy年M月d日", Locale.JAPAN).format(asakatsuStartMillis)
                    Text("開始日", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(dateStr, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.padding(start = 8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Text("バックグラウンド更新の安定化", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            Text("アラーム機能を使用するため、遅延はほとんど発生しませんが、もし止まる場合はバッテリー最適化をオフにしてください。", fontSize = 12.sp, color = Color.LightGray)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    try { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    catch (e: Exception) { Toast.makeText(context, "設定画面を開けませんでした", Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isIgnoringBatteryOpt) Color.DarkGray else MaterialTheme.colorScheme.primary)
            ) { Text(if (isIgnoringBatteryOpt) "最適化はオフになっています" else "バッテリー最適化設定を開く", fontWeight = FontWeight.Bold) }
        }

        Text("ウィジェットの表示", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("年の表示", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = showYear, onCheckedChange = { isChecked -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_SHOW_YEAR] = isChecked }; MimiWidget.forceUpdate(context) } }, enabled = !isAsakatsu)
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("曜日の表示", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = showWeekday, onCheckedChange = { isChecked -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_SHOW_WEEKDAY] = isChecked }; MimiWidget.forceUpdate(context) } }, enabled = !isAsakatsu)
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("更新ボタン (↻) を表示", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = showRefreshBtn, onCheckedChange = { isChecked -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_SHOW_REFRESH_BTN] = isChecked }; MimiWidget.forceUpdate(context) } })
            }
        }

        Text("開発者向け", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("デバッグモードを有効にする", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Switch(checked = debugMode, onCheckedChange = { isChecked -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_DEBUG_MODE_ENABLED] = isChecked } } })
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs by context.dataStore.data.collectAsState(initial = null)
    val scrollState = rememberScrollState()

    val fontFamLyrics = prefs?.get(WidgetDataStore.KEY_FONT_FAMILY_LYRICS) ?: "Serif"
    val fontSizeLyrics = prefs?.get(WidgetDataStore.KEY_FONT_SIZE_LYRICS) ?: 12f
    val fontFamDate = prefs?.get(WidgetDataStore.KEY_FONT_FAMILY_DATE) ?: "Serif"
    val fontSizeDate = prefs?.get(WidgetDataStore.KEY_FONT_SIZE_DATE) ?: 14f
    val fontFamTitle = prefs?.get(WidgetDataStore.KEY_FONT_FAMILY_TITLE) ?: "Serif"
    val fontSizeTitle = prefs?.get(WidgetDataStore.KEY_FONT_SIZE_TITLE) ?: 20f

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 32.dp)) {
        Text("歌詞のフォント", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            FontSettingContent(fontFamLyrics, fontSizeLyrics, 8f..24f,
                { f -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_FONT_FAMILY_LYRICS] = f } } },
                { s -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_FONT_SIZE_LYRICS] = s }; MimiWidget.forceUpdate(context) } }
            )
        }

        Text("日付のフォント", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            FontSettingContent(fontFamDate, fontSizeDate, 10f..30f,
                { f -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_FONT_FAMILY_DATE] = f } } },
                { s -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_FONT_SIZE_DATE] = s }; MimiWidget.forceUpdate(context) } }
            )
        }

        Text("タイトルのフォント", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            FontSettingContent(fontFamTitle, fontSizeTitle, 14f..40f,
                { f -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_FONT_FAMILY_TITLE] = f } } },
                { s -> coroutineScope.launch { context.dataStore.edit { it[WidgetDataStore.KEY_FONT_SIZE_TITLE] = s }; MimiWidget.forceUpdate(context) } }
            )
        }
    }
}

@Composable
fun FontSettingContent(currentFamily: String, currentSize: Float, sizeRange: ClosedFloatingPointRange<Float>, onFamilyChange: (String) -> Unit, onSizeChange: (Float) -> Unit) {
    var localSize by remember(currentSize) { mutableFloatStateOf(currentSize) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onFamilyChange("Serif") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (currentFamily == "Serif") MaterialTheme.colorScheme.primary else Color.DarkGray)) { Text("明朝体") }
        Button(onClick = { onFamilyChange("SansSerif") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (currentFamily == "SansSerif") MaterialTheme.colorScheme.primary else Color.DarkGray)) { Text("ゴシック体") }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text("サイズ: ${localSize.toInt()}sp", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Slider(value = localSize, onValueChange = { localSize = it }, onValueChangeFinished = { onSizeChange(localSize) }, valueRange = sizeRange)
}

// 【大幅変更】タブを追加し、「曲リスト」と「200件の履歴」を見やすく管理できるようにしました。
@Composable
fun SongsSettingsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var songList by remember { mutableStateOf<List<Song>>(emptyList()) }
    val prefs by context.dataStore.data.collectAsState(initial = null)
    val disabledSongs = prefs?.get(WidgetDataStore.KEY_DISABLED_SONGS) ?: emptySet()

    // 履歴データの取得
    val historyJson = prefs?.get(WidgetDataStore.KEY_UPDATE_HISTORY) ?: "[]"
    val type = object : TypeToken<List<UpdateHistory>>() {}.type
    val historyList: List<UpdateHistory> = try {
        Gson().fromJson(historyJson, type) ?: emptyList()
    } catch (e: Exception) { emptyList() }

    var selectedSongTab by remember { mutableStateOf(0) }
    val songTabs = listOf("曲リスト", "履歴 (${historyList.size})")

    LaunchedEffect(Unit) {
        songList = SongRepository(context).fetchAllSongs() ?: emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedSongTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            songTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSongTab == index,
                    onClick = { selectedSongTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }

        if (selectedSongTab == 0) {
            // タブ0: 曲の有効/無効リスト
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { Text("リストに含める曲", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp) }
                items(songList) { song ->
                    val isEnabled = !disabledSongs.contains(song.youtubeId)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable {
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    val currentDisabled = preferences[WidgetDataStore.KEY_DISABLED_SONGS] ?: emptySet()
                                    preferences[WidgetDataStore.KEY_DISABLED_SONGS] = if (isEnabled) currentDisabled + song.youtubeId else currentDisabled - song.youtubeId
                                }
                            }
                        }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            YoutubeThumbnail(
                                youtubeId = song.youtubeId,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = song.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!song.lyrics.isNullOrEmpty()) {
                                    Text(text = song.lyrics.replace("\n", " "), fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Switch(checked = isEnabled, onCheckedChange = null)
                        }
                    }
                }
            }
        } else {
            // タブ1: 最大200件の履歴リスト
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { Text("今までに出てきた曲 (最大200件)", modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp) }
                if (historyList.isEmpty()) {
                    item {
                        Text("まだ履歴がありません", fontSize = 14.sp, color = Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                    }
                } else {
                    items(historyList) { history ->
                        val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(java.util.Date(history.timestamp))

                        // isNewCycle が true の場合は枠線をつけてハイライトします
                        val cardBorder = if (history.isNewCycle) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = cardBorder, // ここに追加
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(dateStr, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(120.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(history.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    // 新クールの時は小さなテキストを追加します
                                    if (history.isNewCycle) {
                                        Text("✨ 新クール開始", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 履歴機能を曲タブに移動したため、デバッグ画面をスッキリさせました。
@Composable
fun DebugScreen(currentVersion: String, latestVersion: String?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var songList by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        songList = SongRepository(context).fetchAllSongs() ?: emptyList()
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {

        Text("アプリ情報", modifier = Modifier.padding(start = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("現在のバージョン", fontSize = 14.sp, color = Color.Gray)
                Text("v$currentVersion", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color.DarkGray, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("GitHubの最新リリース", fontSize = 14.sp, color = Color.Gray)
                Text(if (latestVersion != null) "v$latestVersion" else "取得中/オフライン", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (latestVersion != null && isNewerVersion(latestVersion, currentVersion)) MaterialTheme.colorScheme.primary else Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("手動でウィジェットを更新", modifier = Modifier.padding(start = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        SettingsCard {
            Button(
                onClick = {
                    isLoading = true
                    coroutineScope.launch {
                        if (SongRepository(context).fetchAndSaveRandomSong()) {
                            AlarmScheduler.scheduleNextUpdate(context)
                            MimiWidget.forceUpdate(context)
                            Toast.makeText(context, "ランダムな曲に更新しました", Toast.LENGTH_SHORT).show()
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)
            ) { Text(if (isLoading) "更新中..." else "ランダムな曲を強制セット", fontWeight = FontWeight.Bold) }
        }

        Text("タップして強制上書き", modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 14.sp)
        LazyColumn {
            items(songList) { song ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        coroutineScope.launch {
                            SongRepository(context).saveSpecificSong(song)
                            AlarmScheduler.scheduleNextUpdate(context)
                            MimiWidget.forceUpdate(context)
                            Toast.makeText(context, "${song.title} をセットしました", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text(text = song.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
fun CreditsScreen(currentVersion: String) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        Text("MIMI Widget", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text("Version $currentVersion", color = Color.Gray, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(48.dp))

        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("【開発・デザイン】", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Text("lineside", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(24.dp))

                Text("【Musics】", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Text("MIMI", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(24.dp))

                Text("【Special Thanks】", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Text("はや", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("mon", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("ゆーさぶ", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("モカっくま", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Source Code & Support", color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "This project is developed as open-source software.\nIf you find this widget useful, we would greatly appreciate your support by starring the repository on GitHub.",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lineside0418/Mimi_Widget")))
                        } catch (e: Exception) {
                            Toast.makeText(context, "GitHubを開けませんでした", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = "Star", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Star on GitHub", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("MIMI Official Links", color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@MIMI...official")))
                } catch (e: Exception) {
                    Toast.makeText(context, "YouTubeを開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("YouTube", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/mimi_3mi")))
                } catch (e: Exception) {
                    Toast.makeText(context, "Twitterを開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Twitter (X)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("【注意事項】", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            Text(
                text = "本アプリはファンが作成した非公式アプリケーションであり、アーティスト「MIMI」様および関係者様とは一切関係ありません。\nアプリ内で表示される楽曲、歌詞、画像等の著作権・肖像権は、すべてそれぞれの権利所有者様に帰属します。",
                fontSize = 12.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}