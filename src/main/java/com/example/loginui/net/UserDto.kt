package com.example.loginui.net

data class UserDto(
    val name: String? = null,
    val email: String? = null
)

data class MeResponse(
    val name: String? = null,
    val email: String? = null,
    val client: UserDto? = null,
    val data: UserDto? = null
) {
    fun toUser(): UserDto {
        // 1) flat
        if (name != null || email != null) return UserDto(name, email)
        // 2) nested
        client?.let { return it }
        data?.let { return it }
        // 3) fallback
        return UserDto()
    }
}