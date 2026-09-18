package com.bedrud.app.core.call

import android.telecom.CallEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallEndpointSelectorTest {

    private data class TestEndpoint(val type: Int, val name: String)

    private val earpiece = TestEndpoint(CallEndpoint.TYPE_EARPIECE, "Earpiece")
    private val speaker = TestEndpoint(CallEndpoint.TYPE_SPEAKER, "Speaker")
    private val headset = TestEndpoint(CallEndpoint.TYPE_BLUETOOTH, "Pixel Buds")

    private fun selector() = CallEndpointSelector<TestEndpoint> { endpoint -> endpoint.type }

    @Test
    fun `should return the endpoint at once when it is already announced`() {
        val selector = selector()
        selector.onEndpointsAvailable(listOf(earpiece, speaker))

        assertEquals(speaker, selector.onRouteRequested(CallAudioRoute.SPEAKER))
    }

    @Test
    fun `should hold a request made before any endpoint is announced`() {
        // The audio switch picks a device as it starts up, which can run ahead of Telecom
        // announcing what the call can be routed to.
        val selector = selector()

        assertNull(selector.onRouteRequested(CallAudioRoute.SPEAKER))
        assertEquals(speaker, selector.onEndpointsAvailable(listOf(earpiece, speaker)))
    }

    @Test
    fun `should keep holding a request while no announced endpoint serves it`() {
        val selector = selector()
        selector.onRouteRequested(CallAudioRoute.BLUETOOTH)

        assertNull(selector.onEndpointsAvailable(listOf(earpiece, speaker)))
        assertEquals(headset, selector.onEndpointsAvailable(listOf(earpiece, speaker, headset)))
    }

    @Test
    fun `should serve a held request only once`() {
        val selector = selector()
        selector.onRouteRequested(CallAudioRoute.SPEAKER)
        selector.onEndpointsAvailable(listOf(earpiece, speaker))

        // A second announcement is the headset connecting or dropping, not a reason to route
        // the call back to where it was asked to go minutes ago.
        assertNull(selector.onEndpointsAvailable(listOf(earpiece, speaker, headset)))
    }

    @Test
    fun `should hold the newest request when several are made unserved`() {
        val selector = selector()
        selector.onRouteRequested(CallAudioRoute.BLUETOOTH)
        selector.onRouteRequested(CallAudioRoute.SPEAKER)

        assertEquals(speaker, selector.onEndpointsAvailable(listOf(speaker, headset)))
    }

    @Test
    fun `should forget a held request once the call has ended`() {
        val selector = selector()
        selector.onRouteRequested(CallAudioRoute.SPEAKER)

        selector.reset()

        assertNull(selector.onEndpointsAvailable(listOf(earpiece, speaker)))
    }
}
