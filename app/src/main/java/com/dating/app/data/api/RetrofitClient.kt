package com.dating.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** 后端配置：联调时替换成真实地址即可 */
object ApiConfig {
    // TODO: 换成你的后端地址，结尾必须带 "/"
    const val BASE_URL = "https://api.example-dating.com/v1/"

    // 本机模拟器联调可用：http://10.0.2.2:8080/v1/
}

object RetrofitClient {

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${TokenStore.token}")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

/** 简易 Token 存储（正式项目应接 DataStore/EncryptedSharedPreferences） */
object TokenStore {
    @Volatile
    var token: String = "demo-token"
}
