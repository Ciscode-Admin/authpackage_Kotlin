package com.example.loginui.net

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Requests
data class LoginRequest(
    val email: String,
    val password: String
)

data class SignupRequest(
    val email: String,
    val password: String,
    val name: String? = null,
    val roles: List<String>? = null
)

// Responses
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String
)

data class SignupResponse(
    val id: String,
    val email: String,
    val name: String?,
    val roles: List<String>?
)

@JsonClass(generateAdapter = true)
data class MsExchangeRequest(
    @Json(name = "idToken") val idToken: String
)