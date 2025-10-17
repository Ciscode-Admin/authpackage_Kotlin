package com.example.loginui.net

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    @Volatile private var baseUrlStr: String = ""
    @Volatile internal var overrideToken: String? = null

    @Volatile internal lateinit var cookieJar: CookieJar
    @Volatile internal lateinit var cookieManager: CookieManager

    @Volatile internal var refreshBearer: String? = null
    @JvmStatic fun setRefreshBearer(token: String?) { refreshBearer = token }

    fun social(): SocialConfig = socialCfg

    @JvmStatic
    fun init(context: Context, baseUrl: String) =
        init(context, baseUrl, SocialConfig(), tokenProvider = { null }, onLogout = {})

    @JvmStatic
    fun init(context: Context, baseUrl: String, socialConfig: SocialConfig) =
        init(context, baseUrl, socialConfig, tokenProvider = { null }, onLogout = {})

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

        baseUrlStr = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

        val cm = CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)
        cookieManager = cm
        val jar = JavaNetCookieJar(cm)
        cookieJar = jar

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor(RefreshTokenInterceptor())
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

    @JvmStatic
    fun currentToken(): String? = tokenProvider.invoke()

    @JvmStatic
    fun performLogout() {
        onLogout.invoke()
        onTokenChanged?.invoke(null)
        overrideToken = null
        refreshBearer = null

        runCatching { cookieManager.cookieStore.removeAll() }
    }

    @JvmStatic
    fun notifyTokenChanged(token: String?) {
        overrideToken = token
        val cb = onTokenChanged ?: return
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            cb.invoke(token)
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                cb.invoke(token)
            }
        }
    }

    internal fun baseUrl(): String = baseUrlStr
}

/* -------------------------------------------------------------------------------------------------
 * On 401 (except on /api/auth/refresh-token), call POST /api/auth/refresh-token
 * with NO Authorization header; backend reads HttpOnly refreshToken cookie.
 * Then update access token + retry the original request once.
 * ------------------------------------------------------------------------------------------------ */
class RefreshTokenInterceptor : Interceptor {

    private val isRefreshing = AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.encodedPath.endsWith("/api/auth/refresh-token")) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)
        if (response.code != 401) return response

        response.close()

        if (isRefreshing.compareAndSet(false, true)) {
            try {
                val newToken = refreshAccessToken()
                if (!newToken.isNullOrBlank()) {
                    LoginUi.overrideToken = newToken
                    LoginUi.notifyTokenChanged(newToken)
                }
            } finally {
                isRefreshing.set(false)
            }
        } else {
            var spins = 0
            while (isRefreshing.get() && spins++ < 50) {
                try { Thread.sleep(10) } catch (_: InterruptedException) { break }
            }
        }

        val retryReq = request.newBuilder()
            .removeHeader("Authorization")
            .build()
        return chain.proceed(retryReq)
    }

    private fun refreshAccessToken(): String? {
        val baseUrl = LoginUi.baseUrl()

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .cookieJar(LoginUi.cookieJar)
            .build()

        val req = Request.Builder()
            .url(baseUrl + "api/auth/refresh-token")
            .post("".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                Log.d("REFRESH", "refresh -> HTTP ${resp.code}")
                if (!resp.isSuccessful) return null

                val body = resp.body?.string()?.trim().orEmpty()
                val newAccess = extractJsonValue(body, "accessToken")?.takeIf { it.isNotBlank() }
                newAccess
            }
        } catch (t: Throwable) {
            Log.e("REFRESH", "refresh error: ${t.message}")
            null
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }
}