package com.bedrud.app.core.call

import android.telecom.CallAudioState
import android.telecom.CallEndpoint

/**
 * An audio output this app can route a call to, named once for the two Telecom APIs that name
 * them differently.
 *
 * Telecom asks for an output as a [CallAudioState] route mask below API 34 and as a
 * [CallEndpoint] type from 34 on, and the two numbering schemes do not agree. Keeping the app's
 * own name in between means a caller picks an output rather than an integer, and the only place
 * that has to know which scheme applies is this file.
 */
enum class CallAudioRoute {
    EARPIECE,
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH;

    /**
     * Returns the [CallAudioState] route mask for this output, for the pre-34 routing path.
     */
    @Suppress("DEPRECATION")
    fun toLegacyRoute(): Int = when (this) {
        EARPIECE -> CallAudioState.ROUTE_EARPIECE
        SPEAKER -> CallAudioState.ROUTE_SPEAKER
        WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
    }

    /**
     * Returns the [CallEndpoint] type for this output, for the API 34+ routing path.
     */
    // The types are compile-time constants, so naming them here inlines a number rather than
    // touching a class that does not exist below 34. Only the caller has to be version-gated.
    @Suppress("InlinedApi")
    fun toEndpointType(): Int = when (this) {
        EARPIECE -> CallEndpoint.TYPE_EARPIECE
        SPEAKER -> CallEndpoint.TYPE_SPEAKER
        WIRED_HEADSET -> CallEndpoint.TYPE_WIRED_HEADSET
        BLUETOOTH -> CallEndpoint.TYPE_BLUETOOTH
    }

    /**
     * Returns the first endpoint of [endpoints] carrying this output's type, or null when
     * Telecom is offering none.
     *
     * An endpoint cannot be constructed by this app — Telecom announces the ones that exist, and
     * a routing request has to name one of those. Type is all there is to match on, so two
     * headsets of the same kind resolve to whichever Telecom listed first. Null is a real answer
     * and not a failure: it means the output the user asked for has not been announced yet.
     */
    fun <Endpoint> selectEndpoint(
        endpoints: List<Endpoint>,
        endpointType: (Endpoint) -> Int,
    ): Endpoint? = endpoints.firstOrNull { endpoint -> endpointType(endpoint) == toEndpointType() }
}
