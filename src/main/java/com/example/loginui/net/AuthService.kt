package com.example.loginui.net

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthService {
    @POST("api/auth/clients/login")
    fun login(@Body body: LoginRequest): Call<LoginResponse>

    @POST("api/auth/clients/register")
    fun register(@Body body: SignupRequest): Call<SignupResponse>

    @POST("api/auth/microsoft/exchange")
    fun exchangeMicrosoft(@Body body: MsExchangeRequest): Call<LoginResponse>

    @GET("me")
    fun me(): Call<UserDto>

    @PUT("me")
    fun updateMe(@Body body: UpdateUserRequest): Call<UserDto>
}