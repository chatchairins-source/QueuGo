package com.queuego.rider

/** Network/timeout failures do not prove that the server revoked the session. */
internal class RiderSessionInvalidException(message: String) : IllegalStateException(message)
internal class RiderHttpException(val statusCode: Int, message: String) : IllegalStateException(message)
internal fun shouldClearRiderSession(error: Throwable): Boolean =
    error is RiderSessionInvalidException || (error is RiderHttpException && error.statusCode == 401)
