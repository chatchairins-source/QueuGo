package com.queuego.shared

data class NativeUser(
    val id: String,
    val authUserId: String,
    val name: String,
    val role: String,
    val status: String
)

data class NativeSession(
    val authUserId: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
    val sessionId: String
)

data class NativeAuth(
    val session: NativeSession,
    val user: NativeUser
)
