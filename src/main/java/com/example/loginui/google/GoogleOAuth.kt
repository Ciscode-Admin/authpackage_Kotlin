package com.example.loginui.google

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object GoogleOAuth {
    const val CALLBACK_URI = "restosoft://auth/google/callback"

    @JvmStatic
    fun start(context: Context, baseUrl: String) {
        val base = baseUrl.trim().trimEnd('/')
        val redirect = Uri.encode(CALLBACK_URI)
        val authUrl = "$base/api/auth/google?redirect=$redirect"
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authUrl))
    }

    data class Tokens(val accessToken: String, val refreshToken: String)

    @JvmStatic
    fun parseFromUri(data: Uri?): Tokens? {
        if (data == null) return null
        if (data.scheme != "restosoft" || data.host != "auth" || data.path != "/google/callback") return null
        val access = data.getQueryParameter("accessToken")
        val refresh = data.getQueryParameter("refreshToken")
        return if (!access.isNullOrBlank() && !refresh.isNullOrBlank()) Tokens(access, refresh) else null
    }
}