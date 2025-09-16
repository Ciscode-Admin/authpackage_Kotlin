package com.example.loginui.net

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object LoginUi {
    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var service: AuthService? = null
    @Volatile private var socialCfg: SocialConfig = SocialConfig()

    fun social(): SocialConfig = socialCfg

    fun init(context: Context, baseUrl: String) = init(context, baseUrl, SocialConfig())

    fun init(context: Context, baseUrl: String, socialConfig: SocialConfig) {
        socialCfg = socialConfig
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logger)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        service = retrofit!!.create(AuthService::class.java)
    }

    fun api(): AuthService {
        return service ?: error("LoginUi.init(context, baseUrl) must be called first")
    }
}