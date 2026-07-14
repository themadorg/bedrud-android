package com.bedrud.app.core.livekit

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioManager
import android.telecom.CallAudioState
import com.bedrud.app.core.call.CallConnectionService
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioDeviceChangeListener
import com.twilio.audioswitch.AudioSwitch
import io.livekit.android.audio.AudioHandler

/**
 * Routes call audio via [AudioSwitch]'s legacy AudioManager.setSpeakerphoneOn()/Bluetooth-SCO
 * toggling, instead of LiveKit's default AudioSwitchHandler. On Android 12+, that default
 * switches to AudioManager.setCommunicationDevice(), which on some devices/ROMs reports
 * success without actually rerouting audio, leaving output stuck on the earpiece regardless
 * of what the user picks. The legacy path here works on every device back to API 23 and is
 * the mechanism VoIP apps have relied on for years.
 */
class CallAudioSwitch(private val application: Application) : AudioHandler {

    private var audioSwitch: AudioSwitch? = null
    private val listeners = mutableSetOf<AudioDeviceChangeListener>()

    private val dispatcher: AudioDeviceChangeListener = { devices, selected ->
        listeners.forEach { it(devices, selected) }
        // AudioSwitch can change the selected device on its own (its startup default, or a
        // Bluetooth/wired device connecting/disconnecting), not only when selectDevice() is
        // called explicitly from the UI. Telecom is the actual routing authority for this
        // self-managed call, so every change needs to reach it, not just user-driven ones --
        // otherwise Telecom keeps routing audio to whatever it independently defaulted to
        // (observed: it defaults new calls to earpiece regardless of what AudioSwitch picks).
        selected?.let { CallConnectionService.setAudioRoute(it.toCallAudioRoute()) }
    }

    val availableAudioDevices: List<AudioDevice>
        get() = audioSwitch?.availableAudioDevices.orEmpty()

    val selectedAudioDevice: AudioDevice?
        get() = audioSwitch?.selectedAudioDevice

    override fun start() {
        if (audioSwitch != null) return
        val switch = AudioSwitch(
            context = application,
            loggingEnabled = true,
            // Wired/Bluetooth devices win when present; between the two built-in options,
            // prefer Speakerphone over Earpiece so a bare phone defaults to speaker.
            preferredDeviceList = listOf(
                AudioDevice.BluetoothHeadset::class.java,
                AudioDevice.WiredHeadset::class.java,
                AudioDevice.Speakerphone::class.java,
                AudioDevice.Earpiece::class.java,
            ),
        ).apply {
            audioMode = AudioManager.MODE_IN_COMMUNICATION
            focusMode = AudioManager.AUDIOFOCUS_GAIN
            audioStreamType = AudioManager.STREAM_VOICE_CALL
            audioAttributeUsageType = AudioAttributes.USAGE_VOICE_COMMUNICATION
            audioAttributeContentType = AudioAttributes.CONTENT_TYPE_SPEECH
        }
        audioSwitch = switch
        switch.start(dispatcher)
        switch.activate()
    }

    override fun stop() {
        audioSwitch?.stop()
        audioSwitch = null
    }

    fun selectDevice(device: AudioDevice?) {
        audioSwitch?.selectDevice(device)
    }

    fun registerAudioDeviceChangeListener(listener: AudioDeviceChangeListener) {
        listeners.add(listener)
    }

    fun unregisterAudioDeviceChangeListener(listener: AudioDeviceChangeListener) {
        listeners.remove(listener)
    }
}

private fun AudioDevice.toCallAudioRoute(): Int = when (this) {
    is AudioDevice.BluetoothHeadset -> CallAudioState.ROUTE_BLUETOOTH
    is AudioDevice.WiredHeadset -> CallAudioState.ROUTE_WIRED_HEADSET
    is AudioDevice.Speakerphone -> CallAudioState.ROUTE_SPEAKER
    is AudioDevice.Earpiece -> CallAudioState.ROUTE_EARPIECE
    else -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
}
