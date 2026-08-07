package com.bedrud.app.core.api

import com.google.gson.Gson
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Base for API tests that exercise a [MockWebServer]: it owns the server lifecycle, the Retrofit
 * wiring ([api]), and the recurring method/path/body request assertion ([assertRequest]).
 */
abstract class MockApiTest {

    protected lateinit var server: MockWebServer
    protected val gson: Gson = Gson()

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    /** Builds a Retrofit API for [T] pointed at the mock server. */
    protected inline fun <reified T> api(): T =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(T::class.java)

    /**
     * Asserts the next recorded request's [method] and [path], plus that its body contains every
     * string in [bodyContains]. Returns the request for any further assertions.
     */
    protected fun assertRequest(
        method: String,
        path: String,
        bodyContains: List<String> = emptyList(),
    ): RecordedRequest {
        val request = server.takeRequest()
        assertEquals(method, request.method)
        assertEquals(path, request.path)
        if (bodyContains.isNotEmpty()) {
            val body = request.body.readUtf8()
            bodyContains.forEach { assertTrue(body.contains(it)) }
        }
        return request
    }
}
