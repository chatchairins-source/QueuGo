package com.queuego.rider

import java.io.IOException
import kotlinx.coroutines.CancellationException

internal fun riderMutationAmbiguous(error: Exception): Boolean =
    error is IOException || (error is RiderHttpException && error.statusCode >= 500)

/** A failed response is not success. Only an exact authorized server read can resolve it. */
internal fun <T : Any> riderRecoverMutation(
    mutate: () -> T,
    readCommitted: () -> T?
): T = try {
    mutate()
} catch (failure: Exception) {
    if (!riderMutationAmbiguous(failure)) throw failure
    val committed = try { readCommitted() } catch (readFailure: Exception) {
        if (readFailure is CancellationException) throw readFailure
        failure.addSuppressed(readFailure)
        null
    }
    committed ?: throw failure
}
