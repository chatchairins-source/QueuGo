package com.queuetech.queuego.core.network

class QueueGoApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)
