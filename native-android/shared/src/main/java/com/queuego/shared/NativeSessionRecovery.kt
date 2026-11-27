package com.queuego.shared

/** Only authoritative invalidation clears the persisted session. */
class NativeSessionInvalidException(message: String) : IllegalStateException(message)
class NativeAuthHttpException(val statusCode: Int, message: String) : IllegalStateException(message)
internal fun shouldClearNativeSession(error: Throwable): Boolean =
    error is NativeSessionInvalidException || (error is NativeAuthHttpException && error.statusCode == 401)
