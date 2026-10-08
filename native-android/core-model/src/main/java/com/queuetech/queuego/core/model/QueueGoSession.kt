package com.queuetech.queuego.core.model

data class QueueGoSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val authUserId: String,
    val userId: String,
    val role: AppRole,
)
