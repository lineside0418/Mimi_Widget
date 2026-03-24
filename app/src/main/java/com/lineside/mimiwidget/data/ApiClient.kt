package com.lineside.mimiwidget.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// ネットワーク通信を行うためのRetrofitクライアントを構築するシングルトンオブジェクトです。
object ApiClient {
    // 先生のmicroCMSのサービスID（mimiwidget）を設定したベースURLです。
    private const val BASE_URL = "https://mimiwidget.microcms.io/api/v1/"

    // APIインターフェースの実体を作成します。
    // by lazyにより、最初にアクセスされた時に一度だけ初期化されます。
    val microCmsApi: MicroCmsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // JSONをKotlinのクラスに自動変換します。
            .build()
            .create(MicroCmsApi::class.java)
    }
}