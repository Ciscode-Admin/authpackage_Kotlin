package com.example.loginui.net

import okhttp3.Interceptor
import okhttp3.Response

class AuthHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val token = LoginUi.overrideToken ?: LoginUi.currentToken()

        val outReq = if (!token.isNullOrBlank()) {
            req.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else req

        return chain.proceed(outReq)
    }
}