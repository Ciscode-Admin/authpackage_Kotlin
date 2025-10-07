package com.example.loginui.net

import okhttp3.Interceptor
import okhttp3.Response

class AuthHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val token = LoginUi.currentToken()
        return if (!token.isNullOrBlank()) {
            val newReq = req.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            chain.proceed(newReq)
        } else {
            chain.proceed(req)
        }
    }
}