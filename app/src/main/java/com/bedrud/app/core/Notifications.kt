package com.bedrud.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Registers (or updates) a notification channel with the shared boilerplate. Per-channel extras —
 * lockscreen visibility, vibration, sound, etc. — go in [configure].
 */
fun Context.registerNotificationChannel(
    id: String,
    name: String,
    importance: Int,
    description: String? = null,
    showBadge: Boolean = false,
    configure: NotificationChannel.() -> Unit = {},
) {
    val channel = NotificationChannel(id, name, importance).apply {
        description?.let { this.description = it }
        setShowBadge(showBadge)
        configure()
    }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
