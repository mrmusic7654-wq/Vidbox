package com.vidbox.data.network

import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.model.Errors
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancellation is attached for the entire streaming lifetime, not just until response headers arrive. */
suspend fun <T> Call.withResponse(block: suspend (Response) -> T): T = coroutineScope {
    val call = this@withResponse
    val cancellation = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { call.cancel() }
    }
    try {
        val response = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use { block(it) }
    } finally { cancellation.cancel() }
}

fun Response.requireSuccess() {
    if (isSuccessful) return
    throw Errors.exception(when (code) {
        401 -> ErrorCode.AUTH_REQUIRED
        403, 429 -> ErrorCode.REJECTED
        404, 410 -> ErrorCode.UNAVAILABLE
        408, 504 -> ErrorCode.TIMEOUT
        else -> ErrorCode.NETWORK
    })
}
