package com.ozel.telefon.call

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallUi(
    val state: Int,
    val number: String,
    val displayName: String?,
    val connectTime: Long
)

/** Giden arama meşgul / başarısız bitince gösterilen ekran bilgisi. */
data class EndedUi(val number: String, val displayName: String?, val reason: String)

data class AudioUi(val muted: Boolean = false, val speaker: Boolean = false)

/** Telecom'dan gelen çağrıyı Compose arayüzüne taşıyan basit köprü (tek çağrı). */
object CallState {
    var service: InCallService? = null

    private var call: Call? = null
    private val _ui = MutableStateFlow<CallUi?>(null)
    val ui: StateFlow<CallUi?> = _ui.asStateFlow()

    private val _ended = MutableStateFlow<EndedUi?>(null)
    val ended: StateFlow<EndedUi?> = _ended.asStateFlow()

    fun dismissEnded() {
        _ended.value = null
    }

    private fun captureEnded(c: Call) {
        val details = c.details
        val cause = details.disconnectCause ?: return
        val reason = when (cause.code) {
            DisconnectCause.BUSY -> "Kullanıcı meşgul"
            DisconnectCause.ERROR -> "Arama başarısız"
            else -> return
        }
        if (details.callDirection != Call.Details.DIRECTION_OUTGOING) return
        _ended.value = EndedUi(
            number = details.handle?.schemeSpecificPart ?: "",
            displayName = details.callerDisplayName?.takeIf { it.isNotBlank() },
            reason = reason
        )
    }

    private val _audio = MutableStateFlow(AudioUi())
    val audio: StateFlow<AudioUi> = _audio.asStateFlow()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED) captureEnded(call)
            publish()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    fun attach(c: Call) {
        _ended.value = null
        call?.unregisterCallback(callback)
        call = c
        c.registerCallback(callback)
        publish()
    }

    fun detach(c: Call, remaining: Call?) {
        c.unregisterCallback(callback)
        if (call === c) {
            call = remaining
            remaining?.registerCallback(callback)
        }
        publish()
    }

    fun clear() {
        call?.unregisterCallback(callback)
        call = null
        service = null
        publish()
    }

    @Suppress("DEPRECATION")
    private fun publish() {
        val c = call
        _ui.value = if (c == null) null else {
            val state = if (Build.VERSION.SDK_INT >= 31) c.details.state else c.state
            CallUi(
                state = state,
                number = c.details.handle?.schemeSpecificPart ?: "",
                displayName = c.details.callerDisplayName?.takeIf { it.isNotBlank() },
                connectTime = c.details.connectTimeMillis
            )
        }
    }

    @Suppress("DEPRECATION")
    fun setAudio(state: CallAudioState?) {
        if (state == null) return
        _audio.value = AudioUi(
            muted = state.isMuted,
            speaker = state.route == CallAudioState.ROUTE_SPEAKER
        )
    }

    fun answer() {
        call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        val c = call ?: return
        if (_ui.value?.state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }

    fun hangup() {
        call?.disconnect()
    }

    fun dtmfStart(c: Char) {
        call?.playDtmfTone(c)
    }

    fun dtmfStop() {
        call?.stopDtmfTone()
    }

    fun toggleHold() {
        val c = call ?: return
        if (_ui.value?.state == Call.STATE_HOLDING) c.unhold() else c.hold()
    }

    @Suppress("DEPRECATION")
    fun toggleMute() {
        service?.setMuted(!_audio.value.muted)
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val route = if (_audio.value.speaker) {
            CallAudioState.ROUTE_WIRED_OR_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        service?.setAudioRoute(route)
    }
}
