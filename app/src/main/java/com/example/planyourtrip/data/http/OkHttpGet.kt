package com.example.planyourtrip.data.http

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * [HttpGet] on OkHttp, the app's only HTTP client code. The request runs on OkHttp's own threads (no dispatcher
 * needed), and cancelling the coroutine cancels the call right away (e.g. a new keystroke in the destination field),
 * also while it waits for the server.
 */
class OkHttpGet(private val client: OkHttpClient = defaultClient()) : HttpGet {

    override suspend fun get(url: String, userAgent: String): String {
        val request = try {
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .build()
        } catch (e: IllegalArgumentException) {
            throw IOException("Bad URL: $url", e)
        }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                // After a cancel the continuation is already cancelled, and resuming it does nothing.
                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(
                        runCatching {
                            response.use {
                                if (!it.isSuccessful) throw IOException("HTTP ${it.code} for ${request.url.encodedPath}")
                                it.body?.string().orEmpty()
                            }
                        },
                    )
                }
            })
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 10L

        /** 10 s to connect and 10 s per read. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
