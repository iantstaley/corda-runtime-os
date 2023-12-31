package net.corda.messaging.api.publisher

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * This method is a utility to wait on a list of [CompletableFuture] returned from a [CordaPublisher]. Error handling of
 * such a list is involved and this method simplifies the process. Fatal errors reported by publishers should result in
 * the process being terminated. They are generated by the message bus implementation to inform you that this is no
 * longer a valid client, for instance in cases where another client is already replacing it in a scalable system.
 * Ignoring these errors would put the system in an unusual state.
 *
 * @param futures list of [CompletableFuture] to wait on
 * @param timeout the length of time to wait before timing out
 * @param unit the units of [timeout]
 * @param errorHandler an error handler lambda which is passed any exception and a boolean indicating the failure was
 * fatal.
 */
@Suppress("SpreadOperator")
fun waitOnPublisherFutures(
    futures: List<CompletableFuture<Unit>>,
    timeout: Long,
    unit: TimeUnit,
    errorHandler: (Exception, Boolean) -> Unit
) {
    if (futures.isEmpty()) {
        errorHandler(IllegalArgumentException("Futures list was empty"), false)
        return
    }

    try {
        CompletableFuture.allOf(*futures.toTypedArray()).get(timeout, unit)
    } catch (e: ExecutionException) {
        // One of the futures completed exceptionally
        val firstFatalException = futuresContainFatalError(futures)
        if (firstFatalException == null) {
            // that exception was not fatal
            errorHandler(getExceptionCause(e), false)
        } else {
            // that exception was fatal
            errorHandler(firstFatalException, true)
        }
    } catch (e: Exception) {
        // another error occurred
        errorHandler(e, false)
    }
}

private fun getExceptionCause(e: ExecutionException): Exception =
    if (e.cause is Exception) {
        e.cause as Exception
    } else if (e.cause == null) {
        IllegalStateException("Future completed exceptionally, but cannot retrieve exception")
    } else {
        IllegalStateException("Future completed exceptionally due to non-exception Throwable", e.cause)
    }

private fun futuresContainFatalError(futures: List<CompletableFuture<Unit>>): CordaMessageAPIFatalException? {
    futures.forEach { future ->
        onCompletedWithFatalException(future) { fatalException ->
            return fatalException
        }
    }
    return null
}

private inline fun onCompletedWithFatalException(future: CompletableFuture<Unit>, block:(CordaMessageAPIFatalException) -> Unit) {
    if (future.isCompletedExceptionally) {
        try {
            future.get() // was already completed exceptionally to get here, so no timeout required
        } catch (ex: ExecutionException) {
            if (ex.cause is CordaMessageAPIFatalException) {
                block(ex.cause as CordaMessageAPIFatalException)
            }
        }
    }
}