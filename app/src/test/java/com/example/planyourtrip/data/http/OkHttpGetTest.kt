package com.example.planyourtrip.data.http

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

/** [OkHttpGet] against a local server (no internet). */
class OkHttpGetTest {

    private val server = MockWebServer().apply { start() }
    private val client = OkHttpGet.defaultClient()
    private val http = OkHttpGet(client)

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun returnsTheBody_andSendsUserAgentAndAccept() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val body = http.get(server.url("/v2/currencies").toString(), "PlanYourTrip/1.0 (test)")

        assertEquals("""{"ok":true}""", body)
        val request = server.takeRequest()
        assertEquals("/v2/currencies", request.path)
        assertEquals("PlanYourTrip/1.0 (test)", request.getHeader("User-Agent"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun anErrorStatus_throwsIOException() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            http.get(server.url("/api/").toString(), "ua")
            fail("Expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("503"))
        }
    }

    @Test
    fun aConnectionFailure_throwsIOException() = runBlocking {
        val url = server.url("/api/").toString()
        server.shutdown()
        try {
            http.get(url, "ua")
            fail("Expected an IOException")
        } catch (e: IOException) {
            // Expected.
        }
    }

    @Test
    fun aBadUrl_throwsIOException() = runBlocking {
        try {
            http.get("not a url", "ua")
            fail("Expected an IOException")
        } catch (e: IOException) {
            // Expected.
        }
    }

    @Test
    fun cancelling_cancelsTheRunningCall_rightAway() = runBlocking {
        // The server never answers: only a real cancel ends the call (the read timeout is 10 s).
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val call = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            http.get(server.url("/slow").toString(), "ua")
        }
        // The request reached the server and is waiting for its answer.
        server.takeRequest(5, TimeUnit.SECONDS) ?: fail("The request never arrived")
        assertEquals(1, client.dispatcher.runningCallsCount())

        val started = System.nanoTime()
        call.cancel()
        withTimeout(2_000) { call.join() }

        assertTrue(call.isCancelled)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000)
        // OkHttp has dropped the call (no socket left waiting for an answer).
        withTimeout(2_000) { while (client.dispatcher.runningCallsCount() > 0) Thread.sleep(10) }
    }
}
