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
                val builder = chain.request().newBuilder()
                // 已登录才携带鉴权头；未登录的 /auth/* 请求直接放行
                if (TokenStore.token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer ${TokenStore.token}")
                }
                chain.proceed(builder.build())
            }
            // 全局 401 处理：token 失效时清除登录态（下次启动回到登录页）
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401 && TokenStore.isLogin) {
                    TokenStore.clear()
                }
                response
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

/** Token 存储已拆分到独立文件 TokenStore.kt（持久化 + 登录态管理） */
