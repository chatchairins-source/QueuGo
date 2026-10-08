package com.queuetech.queuego.core.model

data class QueueGoUser(
    val id: String,
    val authUserId: String,
    val role: AppRole,
    val name: String,
    val phone: String,
    val status: String,
)
