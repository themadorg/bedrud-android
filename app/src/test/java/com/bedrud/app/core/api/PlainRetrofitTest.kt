package com.bedrud.app.core.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response
import retrofit2.http.GET

/**
 * Covers what [plainRetrofit] adds over its callers hand-rolling a builder: the trailing slash
 * Retrofit demands of a base URL, and the lenient parsing every Bedrud client shares.
 */
class PlainRetrofitTest : MockApiTest() {

    interface ProbeApi {
        @GET("health")
        suspend fun health(): Response<Probe>
    }

    data class Probe(val status: String? = null)

    private fun probe(baseURL: String): ProbeApi =
        plainRetrofit(baseURL).create(ProbeApi::class.java)

    @Test
    fun `a base url without a trailing slash still resolves the path`() {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        val response = runBlocking { probe(server.url("/api").toString()).health() }

        assertEquals("ok", response.body()?.status)
        assertRequest("GET", "/api/health")
    }

    @Test
    fun `a base url with a trailing slash is not given a second one`() {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        val response = runBlocking { probe(server.url("/api/").toString()).health() }

        assertEquals("ok", response.body()?.status)
        assertRequest("GET", "/api/health")
    }

    @Test
    fun `a body a strict parser would reject still parses`() {
        // Unquoted key and single quotes: accepted by the lenient Gson every client shares,
        // MalformedJsonException under Gson's default strictness.
        server.enqueue(MockResponse().setBody("{status:'ok'}"))

        val response = runBlocking { probe(server.url("/api").toString()).health() }

        assertEquals("ok", response.body()?.status)
    }
}
