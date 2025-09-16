package com.example.loginui.net

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("api/auth/clients/login")
    fun login(@Body body: LoginRequest): Call<LoginResponse>

    @POST("api/auth/clients/register")
    fun register(@Body body: SignupRequest): Call<SignupResponse>

    @POST("api/auth/microsoft/exchange")
    fun exchangeMicrosoft(@Body body: MsExchangeRequest): Call<LoginResponse>
}