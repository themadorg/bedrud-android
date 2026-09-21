package com.bedrud.app.core.call

import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallAudioRouteTest {

    private data class TestEndpoint(val type: Int, val name: String)

    @Suppress("DEPRECATION")
    @Test
    fun `should name the legacy route Telecom expects for every device`() {
        assertEquals(CallAudioState.ROUTE_EARPIECE, CallAudioRoute.EARPIECE.toLegacyRoute())
        assertEquals(CallAudioState.ROUTE_SPEAKER, CallAudioRoute.SPEAKER.toLegacyRoute())
        assertEquals(CallAudioState.ROUTE_WIRED_HEADSET, CallAudioRoute.WIRED_HEADSET.toLegacyRoute())
        assertEquals(CallAudioState.ROUTE_BLUETOOTH, CallAudioRoute.BLUETOOTH.toLegacyRoute())
    }

    @Test
    fun `should name the endpoint type Telecom expects for every device`() {
        assertEquals(CallEndpoint.TYPE_EARPIECE, CallAudioRoute.EARPIECE.toEndpointType())
        assertEquals(CallEndpoint.TYPE_SPEAKER, CallAudioRoute.SPEAKER.toEndpointType())
        assertEquals(CallEndpoint.TYPE_WIRED_HEADSET, CallAudioRoute.WIRED_HEADSET.toEndpointType())
        assertEquals(CallEndpoint.TYPE_BLUETOOTH, CallAudioRoute.BLUETOOTH.toEndpointType())
    }

    @Test
    fun `should select the endpoint whose type matches the route`() {
        val endpoints = listOf(
            TestEndpoint(CallEndpoint.TYPE_EARPIECE, "Earpiece"),
            TestEndpoint(CallEndpoint.TYPE_SPEAKER, "Speaker"),
            TestEndpoint(CallEndpoint.TYPE_BLUETOOTH, "Pixel Buds"),
        )

        val selected = CallAudioRoute.BLUETOOTH.selectEndpoint(endpoints) { endpoint -> endpoint.type }

        assertEquals(endpoints[2], selected)
    }

    @Test
    fun `should select nothing when no endpoint carries that type`() {
        // A headset the user asked for before it finished connecting: the request has to wait
        // for the endpoint to appear rather than fall back to some other output.
        val endpoints = listOf(TestEndpoint(CallEndpoint.TYPE_EARPIECE, "Earpiece"))

        val selected = CallAudioRoute.BLUETOOTH.selectEndpoint(endpoints) { endpoint -> endpoint.type }

        assertNull(selected)
    }

    @Test
    fun `should select the first endpoint when two share a type`() {
        // Two paired Bluetooth headsets are one indistinguishable ROUTE_BLUETOOTH to the legacy
        // API, and two separate endpoints here. Routing by type alone can only take the first.
        val endpoints = listOf(
            TestEndpoint(CallEndpoint.TYPE_BLUETOOTH, "Pixel Buds"),
            TestEndpoint(CallEndpoint.TYPE_BLUETOOTH, "Car"),
        )

        val selected = CallAudioRoute.BLUETOOTH.selectEndpoint(endpoints) { endpoint -> endpoint.type }

        assertEquals(endpoints[0], selected)
    }

    @Test
    fun `should select nothing when the endpoint list is empty`() {
        // Telecom has not announced any endpoint yet, which is the state a request made right
        // after the connection is created lands in.
        val selected = CallAudioRoute.SPEAKER
            .selectEndpoint(emptyList<TestEndpoint>()) { endpoint -> endpoint.type }

        assertNull(selected)
    }
}
