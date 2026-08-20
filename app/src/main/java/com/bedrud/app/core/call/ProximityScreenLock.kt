package com.bedrud.app.core.call

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

/**
 * Blanks the screen while the phone is held against a face, the way the dialer does.
 *
 * `PROXIMITY_SCREEN_OFF_WAKE_LOCK` hands the whole behaviour to the platform: for as long as the
 * lock is held, the display turns off when the proximity sensor is covered and comes back when it
 * clears, and touches are ignored in between — which is the point, since an ear resting on the
 * glass would otherwise press whatever it lands on.
 *
 * Not every device carries the sensor, so the lock is only created where the platform reports the
 * level as supported; everywhere else this quietly does nothing rather than failing.
 */
class ProximityScreenLock(context: Context, private val holdTimeoutMs: Long) {

    private val wakeLock: PowerManager.WakeLock? =
        context.getSystemService(PowerManager::class.java)
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }

    /**
     * Holds the lock while [enabled] and releases it as soon as it is not. Safe to call repeatedly
     * with the same value — the audio route this follows re-announces itself on every change.
     *
     * [holdTimeoutMs] is a backstop, not the mechanism: the caller releases the lock when the route
     * changes and when the call ends, and the timeout only covers a process that dies without
     * reaching either. A screen stuck dark is worse than one that comes back early.
     */
    // The lock outlives this method by design — it is held across route changes and released by the
    // owner (CallService.onDestroy), which lint's single-method flow analysis cannot see.
    @SuppressLint("Wakelock")
    fun setEnabled(enabled: Boolean) {
        val lock = wakeLock ?: return
        if (enabled) {
            if (!lock.isHeld) lock.acquire(holdTimeoutMs)
        } else if (lock.isHeld) {
            // Waiting for the sensor to clear stops the screen flashing back on while the phone is
            // still at the ear — on the way out of a call, that flash lands on the user's cheek.
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    private companion object {
        const val WAKE_LOCK_TAG = "bedrud:proximity"
    }
}
