package com.bedrud.app.core.call

/**
 * Answers which endpoint a routing request should name, holding onto a request Telecom cannot
 * serve yet.
 *
 * Telecom announces the endpoints a call may be routed to; the app cannot name one it has not
 * been given. Meanwhile the audio switch picks an output as it starts up, which regularly runs
 * ahead of that announcement, and a device the user chose may only connect later. Both cases
 * leave a wanted output with no endpoint behind it, and this remembers the request until one
 * appears instead of dropping it.
 *
 * A held request is served once — a later announcement is some other device connecting or
 * dropping, not a reason to re-route the call to a choice the user has since moved on from.
 *
 * Generic over the endpoint so the decision can be exercised without Telecom; the connection
 * supplies `android.telecom.CallEndpoint` and its type. Not thread-safe: it belongs to the
 * connection that owns it, whose callbacks arrive in order.
 */
class CallEndpointSelector<Endpoint>(private val endpointType: (Endpoint) -> Int) {

    private var availableEndpoints: List<Endpoint> = emptyList()
    private var requestedRoute: CallAudioRoute? = null

    /**
     * Records that [route] is the output wanted, and returns the endpoint to request now, or
     * null while nothing announced can serve it.
     */
    fun onRouteRequested(route: CallAudioRoute): Endpoint? {
        val endpoint = route.selectEndpoint(availableEndpoints, endpointType)
        requestedRoute = if (endpoint == null) route else null
        return endpoint
    }

    /**
     * Records the endpoints Telecom is offering, and returns the one a held request was waiting
     * for, or null when nothing was held or none of them serves it.
     */
    fun onEndpointsAvailable(endpoints: List<Endpoint>): Endpoint? {
        availableEndpoints = endpoints
        val route = requestedRoute ?: return null
        val endpoint = route.selectEndpoint(endpoints, endpointType) ?: return null
        requestedRoute = null
        return endpoint
    }

    /**
     * Forgets everything, for a connection that is going away.
     */
    fun reset() {
        availableEndpoints = emptyList()
        requestedRoute = null
    }
}
