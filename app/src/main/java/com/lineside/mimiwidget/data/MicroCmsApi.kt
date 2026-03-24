package com.lineside.mimiwidget.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// microCMSのエンドポイントと通信するためのAPIインターフェースです。
interface MicroCmsApi {

    // 登録されている曲のリストを取得します。
    // X-MICROCMS-API-KEYをヘッダーに付与して認証を行います。
    @GET("songs")
    suspend fun getSongs(
        @Header("X-MICROCMS-API-KEY") apiKey: String,
        @Query("limit") limit: Int = 100 // 必要に応じて取得上限件数を変更します。
    ): SongResponse
}