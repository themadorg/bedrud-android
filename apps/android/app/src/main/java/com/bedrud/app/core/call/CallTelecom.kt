package com.bedrud.app.core.call

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
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

    fun registerPhoneAccount(context: Context) {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
        val handle = phoneAccountHandle(context)
        val builder = PhoneAccount.builder(handle, context.getString(R.string.call_phone_account_label))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_call_notification))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
        }
        try {
            telecom.registerPhoneAccount(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register PhoneAccount", e)
        }
    }
}