package com.example.loginui.net

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

typealias TokenProvider = () -> String?
typealias OnLogout = () -> Unit
typealias OnTokenChanged = (String?) -> Unit

object LoginUi {
    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var service: AuthService? = null
    @Volatile private var socialCfg: SocialConfig = SocialConfig()
    @Volatile private var tokenProvider: TokenProvider = { null }
    @Volatile private var onLogout: OnLogout = {}
    @Volatile private var onTokenChanged: OnTokenChanged? = null

    fun social(): SocialConfig = socialCfg


    @JvmStatic
    fun init(context: Context, baseUrl: String) =
        init(context, baseUrl, SocialConfig(), tokenProvider = { null }, onLogout = {})

    @JvmStatic
    fun init(context: Context, baseUrl: String, socialConfig: SocialConfig) =
        init(context, baseUrl, socialConfig, tokenProvider = { null }, onLogout = {})

    // ---- New init with hooks ----
    @JvmStatic
    fun init(
        context: Context,
        baseUrl: String,
        socialConfig: SocialConfig,
        tokenProvider: TokenProvider,
        onLogout: OnLogout,
        onTokenChanged: OnTokenChanged? = null
    ) {
        socialCfg = socialConfig
        this.tokenProvider = tokenProvider
        this.onLogout = onLogout
        this.onTokenChanged = onTokenChanged

        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(AuthHeaderInterceptor())
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

    @JvmStatic
    fun api(): AuthService =
        service ?: error("LoginUi.init(...) must be called first")

    // ---- Token/Logout utilities for library & host ----
    @JvmStatic
    fun currentToken(): String? = tokenProvider.invoke()

    @JvmStatic
    fun performLogout() {
        onLogout.invoke()
        onTokenChanged?.invoke(null)
    }

    @JvmStatic
    fun notifyTokenChanged(token: String?) {
        onTokenChanged?.invoke(token)
    }
}