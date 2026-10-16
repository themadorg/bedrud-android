package com.bedrud.app.core.call

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bedrud.app.MainActivity
import com.bedrud.app.R
import com.bedrud.app.core.createLocaleContext
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.livekit.RoomManager
import com.bedrud.app.core.recent.RecentRoomsStore
import com.bedrud.app.core.registerNotificationChannel
import com.bedrud.app.core.rooms.JoinFailureRelay
import com.bedrud.app.ui.screens.settings.SettingsStore
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioDeviceChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.android.inject

class CallService : Service() {

    private val instanceManager: InstanceManager by inject()
    private val recentRoomsStore: RecentRoomsStore by inject()
    private val joinFailureRelay: JoinFailureRelay by inject()
    private var roomManager: RoomManager? = null
    private var session: CallSession? = null
    private var serviceScope: CoroutineScope? = null
    private var callStartTime: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private var proximityScreenLock: ProximityScreenLock? = null
    private var audioRouteListener: AudioDeviceChangeListener? = null

    // Carries out what the call's session decides, with the parts only a service can do.
    private val sessionHost = object : CallSession.Host {
        override fun stop() {
            Log.d(TAG, "Connection failed; stopping foreground service")
            stopSelf()
        }

        override fun hangUp() {
            Log.d(TAG, "Room disconnected by server, stopping service")
            this@CallService.hangUp()
        }

        override fun showMuted(isMuted: Boolean) {
            updateForegroundNotification(isMuted = isMuted)
        }

        override fun reportRemoval() {
            joinFailureRelay.report(getString(R.string.meeting_state_kickedMessage), isRemoval = true)
        }

        override fun onConnected() {
            roomManager?.let(::bindProximityBlanking)
        }
    }

    // The system hands a service its own context in the device's language, not the one picked in
    // the app, so the call notification needs the same wrapping MainActivity applies to itself.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.createLocaleContext(SettingsStore(base).getLanguageTag()))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HANG_UP -> {
                hangUp()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                session?.toggleMute()
                return START_STICKY
            }
            ACTION_RETURN_TO_MEETING -> {
                updateForegroundNotification()
                return START_STICKY
            }
        }

        val roomName = intent?.getStringExtra(EXTRA_ROOM_NAME) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL)

        if (isRunning && activeRoomName == roomName) {
            // Already in this meeting — keep the foreground call alive.
            updateForegroundNotification()
            return START_STICKY
        }

        val rm = instanceManager.roomManager.value ?: run {
            Log.e(TAG, "No RoomManager available")
            stopSelf()
            return START_NOT_STICKY
        }
        roomManager = rm
        activeRoomName = roomName
        activeInstanceId = instanceManager.store.activeInstance?.id
        isRunning = true
        callStartTime = SystemClock.elapsedRealtime()

        // Made with the room manager, so the two are always set together and onDestroy can leave
        // the room through the session whichever way the call ended.
        serviceScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        serviceScope = scope
        val callSession = CallSession(rm, scope, sessionHost)
        session = callSession

        createNotificationChannel()
        CallTelecom.registerPhoneAccount(this)
        val callPlaced = CallConnectionService.placeCall(this, roomName)
        if (!callPlaced) {
            Log.e(TAG, "Telecom placeCall failed; stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        startCallForeground(roomName)

        acquireWakeLock()

        callSession.start(url, token, roomName, avatarUrl)
        CallConnectionService.muteListener = callSession::onTelecomMute

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        val endedRoomName = activeRoomName
        val endedInstanceId = activeInstanceId
        activeRoomName = null
        activeInstanceId = null

        if (endedRoomName != null && endedInstanceId != null) {
            recentRoomsStore.markLeft(endedRoomName, endedInstanceId)
        }

        releaseWakeLock()
        // Before roomManager is dropped: the listener is unregistered from its audio handler.
        releaseProximityBlanking()

        session?.end(userHungUp = userInitiatedHangUp)
        session = null
        roomManager = null
        userInitiatedHangUp = false

        CallConnectionService.muteListener = null
        CallConnectionService.endCall()

        serviceScope?.cancel()
        serviceScope = null

        Log.d(TAG, "CallService destroyed")
    }

    private fun hangUp() {
        userInitiatedHangUp = true
        stopSelf()
    }

    private fun startCallForeground(roomName: String) {
        val notification = buildNotification(roomName, isMuted = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(isMuted: Boolean = roomManager?.isMicEnabled?.value == false) {
        val roomName = activeRoomName ?: return
        val notification = buildNotification(roomName, isMuted)
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Blanks the screen on proximity, but only while the earpiece is the output.
     *
     * That route is the one that means the phone is at an ear; speaker, wired and Bluetooth all
     * mean it is not, and blanking on those would darken a phone sitting on the desk in front of
     * its user.
     */
    private fun bindProximityBlanking(roomManager: RoomManager) {
        val audioHandler = roomManager.audioHandler ?: return
        val lock = proximityScreenLock
            ?: ProximityScreenLock(this, MAX_CALL_DURATION_MS).also { proximityScreenLock = it }
        val listener = audioRouteListener ?: run {
            val created: AudioDeviceChangeListener = { _, selected ->
                val atTheEar = selected is AudioDevice.Earpiece
                Log.d(TAG, "Audio route ${selected?.javaClass?.simpleName}; proximity blanking=$atTheEar")
                lock.setEnabled(atTheEar)
            }
            audioRouteListener = created
            created
        }
        audioHandler.registerAudioDeviceChangeListener(listener)
        lock.setEnabled(audioHandler.selectedAudioDevice is AudioDevice.Earpiece)
    }

    private fun releaseProximityBlanking() {
        audioRouteListener?.let { listener ->
            roomManager?.audioHandler?.unregisterAudioDeviceChangeListener(listener)
        }
        audioRouteListener = null
        proximityScreenLock?.setEnabled(false)
        proximityScreenLock = null
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(MAX_CALL_DURATION_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        registerNotificationChannel(
            id = CHANNEL_ID,
            name = getString(R.string.call_channel_name),
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            description = getString(R.string.call_channel_description),
        ) {
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            enableVibration(false)
            setSound(null, null)
        }
    }

    private fun buildNotification(roomName: String, isMuted: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_RETURN_TO_MEETING
                putExtra(EXTRA_ROOM_NAME, roomName)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val muteAction = NotificationCompat.Action.Builder(
            if (isMuted) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off,
            if (isMuted) getString(R.string.call_action_unmute) else getString(R.string.call_action_mute),
            toggleMuteIntent(this),
        ).build()

        val hangUpAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.call_action_hangUp),
            hangUpIntent(this),
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.call_notification_title, roomName))
            .setContentText(getString(R.string.call_notification_text))
            .setSmallIcon(R.drawable.ic_call_notification)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - callStartTime))
            .setContentIntent(contentIntent)
            .addAction(muteAction)
            .addAction(hangUpAction)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    companion object {
        private const val TAG = "CallService"
        private const val WAKE_LOCK_TAG = "bedrud:active_call"
        private const val CHANNEL_ID = "bedrud_call_ongoing"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_ROOM_NAME = "room_name"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_AVATAR_URL = "avatar_url"
        const val ACTION_HANG_UP = "com.bedrud.app.HANG_UP"
        const val ACTION_TOGGLE_MUTE = "com.bedrud.app.TOGGLE_MUTE"
        private const val TOGGLE_MUTE_REQUEST = 1
        private const val HANG_UP_REQUEST = 2

        private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context,
                requestCode,
                Intent(context, CallService::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Turns the call's microphone over, from the notification or the picture-in-picture window. */
        fun toggleMuteIntent(context: Context): PendingIntent =
            actionIntent(context, ACTION_TOGGLE_MUTE, TOGGLE_MUTE_REQUEST)

        /** Hangs up the call, from the notification or the picture-in-picture window. */
        fun hangUpIntent(context: Context): PendingIntent =
            actionIntent(context, ACTION_HANG_UP, HANG_UP_REQUEST)
        const val ACTION_RETURN_TO_MEETING = "com.bedrud.app.RETURN_TO_MEETING"
        // The ceiling both of the call's wake locks fall back on: no meeting runs this long, so
        // reaching it means the service died without releasing, not that a call is still up.
        internal const val MAX_CALL_DURATION_MS = 8 * 60 * 60 * 1000L

        var isRunning = false
            private set

        var activeRoomName: String? = null
            private set

        var activeInstanceId: String? = null
            private set

        private var userInitiatedHangUp = false

        fun start(context: Context, roomName: String, url: String, token: String, avatarUrl: String? = null) {
            val intent = Intent(context, CallService::class.java).apply {
                putExtra(EXTRA_ROOM_NAME, roomName)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TOKEN, token)
                avatarUrl?.let { putExtra(EXTRA_AVATAR_URL, it) }
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            userInitiatedHangUp = true
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}