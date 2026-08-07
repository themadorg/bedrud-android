package com.bedrud.app.core.call

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.bedrud.app.R

object CallTelecom {
    private const val TAG = "CallTelecom"
    private const val PHONE_ACCOUNT_ID = "bedrud_call"

    fun phoneAccountHandle(context: Context): PhoneAccountHandle {
        val componentName = ComponentName(context, CallConnectionService::class.java)
        return PhoneAccountHandle(componentName, PHONE_ACCOUNT_ID)
    }

    // CAPABILITY_SELF_MANAGED is deprecated in favour of the androidx.core.telecom
    // CallsManager API, which is a rewrite of how this app registers and runs calls rather
    // than a swap at this call site - see the note on setAudioRoute in
    // CallConnectionService. Until that happens, self-managed registration is what makes
    // meetings behave as real calls, so it stays.
    @Suppress("DEPRECATION")
    fun registerPhoneAccount(context: Context) {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
        val handle = phoneAccountHandle(context)
        val builder = PhoneAccount.builder(handle, context.getString(R.string.call_phone_account_label))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_call_notification))
        // No SDK_INT guard: this capability arrived in API 26 and minSdk is 28.
        builder.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
        try {
            telecom.registerPhoneAccount(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register PhoneAccount", e)
        }
    }
}