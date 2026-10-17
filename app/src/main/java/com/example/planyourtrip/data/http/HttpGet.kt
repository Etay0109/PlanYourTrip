package com.example.planyourtrip.data.http

import java.io.IOException

/**
 * One HTTP GET returning the body. Throws [IOException] on a connection failure or an HTTP error status. Every HTTP
 * API class (FrankfurterRatesApi, PhotonPlaceSearch, WikipediaPhotoSource) uses it; the app uses [OkHttpGet], tests
 * pass fakes.
 */
fun interface HttpGet {
    suspend fun get(url: String, userAgent: String): String
}
