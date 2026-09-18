package com.bedrud.app.core.call

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.bedrud.app.R
import com.bedrud.app.core.deeplink.BedrudScheme

class CallConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val roomName = parseRoomName(request?.address)
            ?: getString(R.string.call_default_room_name)
        val connection = BedrudConnection(application, roomName)
        connection.setInitializing()
        connection.setDialing()
        connection.setActive()
        activeConnection = connection
        pendingRoute?.let { route ->
            pendingRoute = null
            connection.adoptPendingRoute(route)
        }
        Log.d(TAG, "Outgoing connection active for room: $roomName")
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        Log.e(TAG, "Failed to create outgoing connection")
        CallService.stop(applicationContext)
    }

    private class BedrudConnection(
        private val application: Application,
        private val roomName: String,
    ) : Connection() {

        // API 34+ only: the output still waiting for an endpoint Telecom can route it to. Owned
        // by the connection, so nothing is carried over into the next call.
        private var endpointSelector: CallEndpointSelector<CallEndpoint>? = null

        // Below API 34: the route asked for before Telecom started honouring requests on this
        // connection, flushed from onCallAudioStateChanged.
        private var pendingLegacyRoute: CallAudioRoute? = null

        /**
         * Returns [endpoint]'s type, for the selector to match a wanted output against.
         */
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun endpointTypeOf(endpoint: CallEndpoint): Int = endpoint.endpointType

        /**
         * Asks Telecom to route this call's audio to [endpoint], and reports what came of it.
         *
         * Unlike the route mask it replaces, this request is answered: a refusal arrives as a
         * [CallEndpointException] carrying a reason, which is the whole reason this path is
         * better than the one below API 34.
         */
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun changeEndpoint(endpoint: CallEndpoint) {
            Log.d(TAG, "Requesting endpoint ${endpoint.endpointName} type=${endpoint.endpointType}")
            requestCallEndpointChange(
                endpoint,
                application.mainExecutor,
                object : OutcomeReceiver<Void, CallEndpointException> {
                    override fun onResult(result: Void?) {
                        Log.d(TAG, "Call audio routed to ${endpoint.endpointName}")
                    }

                    override fun onError(error: CallEndpointException) {
                        Log.e(
                            TAG,
                            "Failed to route call audio to ${endpoint.endpointName} " +
                                "code=${error.code}",
                            error
                        )
                    }
                },
            )
        }

        /**
         * Routes this call's audio to [route] through the pre-34 route mask.
         */
        @Suppress("DEPRECATION")
        private fun setLegacyAudioRoute(route: CallAudioRoute) {
            try {
                setAudioRoute(route.toLegacyRoute())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set audio route", e)
            }
        }

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                endpointSelector = CallEndpointSelector(::endpointTypeOf)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connectionProperties = PROPERTY_SELF_MANAGED
            }
            connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioModeIsVoip = true
            }
            val address = roomUri(roomName)
            setCallerDisplayName(roomName, TelecomManager.PRESENTATION_ALLOWED)
            setAddress(address, TelecomManager.PRESENTATION_ALLOWED)
        }

        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
            endpointSelector?.reset()
            pendingLegacyRoute = null
            activeConnection = null
            CallService.stop(application)
        }

        override fun onAbort() {
            onDisconnect()
        }

        override fun onMuteStateChanged(isMuted: Boolean) {
            muteListener?.invoke(isMuted)
        }

        // Deprecated alongside setAudioRoute, and kept for the devices below API 34 that have no
        // endpoint callbacks. Telecom still delivers this to a self-managed Connection, and it is
        // the only signal that routing requests will now be honoured there.
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCallAudioStateChanged(state: android.telecom.CallAudioState?) {
            // LiveKit manages capture/playback; system routes call audio.
            Log.d(
                TAG,
                "onCallAudioStateChanged route=${state?.route} " +
                    "supportedRouteMask=${state?.supportedRouteMask} " +
                    "isMuted=${state?.isMuted}"
            )
            // Telecom silently ignores setAudioRoute() calls made before it has sent this
            // connection its first audio-state callback (observed: a request made right after
            // the Connection is created has no effect, even though activeConnection is already
            // non-null by then). This callback is the actual signal that Telecom is now
            // listening, so apply anything that was requested before that point now.
            pendingLegacyRoute?.let { route ->
                pendingLegacyRoute = null
                Log.d(TAG, "Flushing pending audio route=$route")
                setLegacyAudioRoute(route)
            }
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun onCallEndpointChanged(endpoint: CallEndpoint) {
            Log.d(TAG, "Call audio now on ${endpoint.endpointName} type=${endpoint.endpointType}")
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun onAvailableCallEndpointsChanged(endpoints: List<CallEndpoint>) {
            Log.d(
                TAG,
                "Available endpoints: " +
                    endpoints.joinToString { endpoint -> endpoint.endpointName }
            )
            endpointSelector?.onEndpointsAvailable(endpoints)?.let(::changeEndpoint)
        }

        /**
         * Applies [route], which was asked for before this connection existed.
         *
         * Below API 34 it only records the request: Telecom ignores a route set this early, and
         * [onCallAudioStateChanged] is what flushes it.
         */
        fun adoptPendingRoute(route: CallAudioRoute) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                routeTo(route)
            } else {
                pendingLegacyRoute = route
            }
        }

        /**
         * Routes this call's audio to [route], or holds the request until an endpoint that can
         * serve it is announced.
         */
        fun routeTo(route: CallAudioRoute) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val endpoint = endpointSelector?.onRouteRequested(route)
                if (endpoint == null) {
                    Log.d(TAG, "No endpoint for $route announced yet; holding the request")
                    return
                }
                changeEndpoint(endpoint)
            } else {
                setLegacyAudioRoute(route)
            }
        }
    }

    companion object {
        private const val TAG = "CallConnectionService"
        const val SCHEME = BedrudScheme.SCHEME
        private const val ROOM_URI_AUTHORITY = "room"
        private var activeConnection: BedrudConnection? = null
        private var pendingRoute: CallAudioRoute? = null
        var muteListener: ((Boolean) -> Unit)? = null

        fun placeCall(context: Context, roomName: String): Boolean {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
            CallTelecom.registerPhoneAccount(context)

            val address = roomUri(roomName)
            val extras = android.os.Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, CallTelecom.phoneAccountHandle(context))
            }

            return try {
                telecom.placeCall(address, extras)
                Log.d(TAG, "Placed self-managed call for room: $roomName")
                true
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot place call - missing permission", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to place call", e)
                false
            }
        }

        fun endCall() {
            try {
                activeConnection?.apply {
                    setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                    destroy()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to end call connection", e)
            }
            activeConnection = null
            pendingRoute = null
        }

        fun updateMuteState(muted: Boolean) {
            Log.d(TAG, "updateMuteState muted=$muted activeConnection=${activeConnection != null}")
            try {
                activeConnection?.setActive()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update mute state", e)
            }
        }

        /**
         * Routes call audio to [route] through our self-managed [Connection]. Plain
         * AudioManager-level routing (AudioSwitch's setSpeakerphoneOn / setCommunicationDevice)
         * can silently lose to a connected Bluetooth SCO headset, which the platform's own
         * CallAudioRouteController otherwise auto-prioritizes for any active call. Going through
         * the Connection gives this app the same routing authority a system dialer has, which is
         * what actually overrides that priority.
         *
         * From API 34 the connection names a [CallEndpoint] Telecom announced; below that it sets
         * a route mask. Both paths ship — minSdk is 28 — and the property above is what either
         * one has to preserve. The remaining deprecation, `CAPABILITY_SELF_MANAGED`, has no
         * replacement on these classes and needs the androidx.core.telecom CallsManager API,
         * which replaces this whole ConnectionService rather than any single call.
         */
        fun setAudioRoute(route: CallAudioRoute) {
            Log.d(TAG, "setAudioRoute requested=$route activeConnection=${activeConnection != null}")
            val connection = activeConnection
            if (connection == null) {
                // AudioSwitch's startup device selection can race ahead of connection
                // creation; remember the request and apply it once onCreateOutgoingConnection
                // hands us a Connection, instead of silently dropping it.
                pendingRoute = route
                return
            }
            connection.routeTo(route)
        }

        fun roomUri(roomName: String): Uri =
            Uri.parse("$SCHEME://$ROOM_URI_AUTHORITY/${Uri.encode(roomName)}")

        private fun parseRoomName(address: Uri?): String? {
            address ?: return null
            return when (address.scheme) {
                SCHEME -> address.lastPathSegment?.let(Uri::decode)?.takeIf { it.isNotBlank() }
                "tel" -> address.schemeSpecificPart?.takeIf { it.isNotBlank() }
                else -> address.lastPathSegment?.let(Uri::decode)?.takeIf { it.isNotBlank() }
                    ?: address.schemeSpecificPart?.takeIf { it.isNotBlank() }
            }
        }
    }
}