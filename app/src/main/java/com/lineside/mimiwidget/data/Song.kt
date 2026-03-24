package com.lineside.mimiwidget.data

// APIから取得する曲の基本データを保持するクラスです。
data class Song(
    val id: String,
    val title: String,
    val lyrics: String?,
    val youtubeId: String // microCMSで設定したYouTubeの動画IDを受け取ります。
) {
    // YouTubeの動画IDからサムネイル画像のURLを自動生成するプロパティです。
    // hqdefault.jpgは高画質のサムネイルを取得するための標準的なファイル名です。
    val thumbnailUrl: String
        get() = "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"

    // YouTubeの動画IDから実際の動画リンクを自動生成するプロパティです。
    // ウィジェットをタップした時にYouTubeを開くために使用します。
    val videoUrl: String
        get() = "https://www.youtube.com/watch?v=$youtubeId"
}

// microCMSのリスト取得APIからの応答全体を保持するクラスです。
data class SongResponse(
    val contents: List<Song>
)