package com.example.planyourtrip.data.http

/** Answers every request with [body] and records each URL and User-Agent. No network. */
class RecordingHttpGet(private val body: String) : HttpGet {
    val urls = mutableListOf<String>()
    val userAgents = mutableListOf<String>()

    override suspend fun get(url: String, userAgent: String): String {
        urls += url
        userAgents += userAgent
        return body
    }
}
