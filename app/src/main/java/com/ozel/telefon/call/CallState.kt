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

/** Telecom'dan gelen çağrıları Compose arayüzüne taşıyan köprü. Birden çok çağrıyı (bekleyen, birleştirilen) izler. */
object CallState {
    var service: InCallService? = null

    private val calls = mutableListOf<Call>()

    /** Ekranda gösterilen asıl çağrı. */
    private val _ui = MutableStateFlow<CallUi?>(null)
    val ui: StateFlow<CallUi?> = _ui.asStateFlow()

    /** Asıl çağrının yanındaki ikinci çağrı (beklemede olan ya da çağrı bekleyen). */
    private val _other = MutableStateFlow<CallUi?>(null)
    val other: StateFlow<CallUi?> = _other.asStateFlow()

    private val _canMerge = MutableStateFlow(false)
    val canMerge: StateFlow<Boolean> = _canMerge.asStateFlow()

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

    @Suppress("DEPRECATION")
    private fun st(c: Call): Int = if (Build.VERSION.SDK_INT >= 31) c.details.state else c.state

    /** Konferansın alt çağrıları ayrı gösterilmez; yalnız konferans çağrısı görünür. */
    private fun visible(): List<Call> = calls.filter { it.parent == null }

    private fun primary(): Call? {
        val v = visible()
        return v.firstOrNull { st(it) == Call.STATE_RINGING }
            ?: v.firstOrNull {
                st(it) == Call.STATE_ACTIVE || st(it) == Call.STATE_DIALING ||
                    st(it) == Call.STATE_CONNECTING || st(it) == Call.STATE_PULLING_CALL
            }
            ?: v.firstOrNull { st(it) == Call.STATE_HOLDING }
            ?: v.lastOrNull()
    }

    private fun secondary(p: Call?): Call? {
        val l = visible().filter {
            it !== p && st(it) != Call.STATE_DISCONNECTED && st(it) != Call.STATE_DISCONNECTING
        }
        return l.firstOrNull { st(it) == Call.STATE_RINGING }
            ?: l.firstOrNull { st(it) == Call.STATE_HOLDING }
            ?: l.firstOrNull()
    }

    private fun toUi(c: Call): CallUi {
        val d = c.details
        val conf = d.hasProperty(Call.Details.PROPERTY_CONFERENCE)
        return CallUi(
            state = st(c),
            number = d.handle?.schemeSpecificPart ?: "",
            displayName = d.callerDisplayName?.takeIf { it.isNotBlank() }
                ?: if (conf) "Konferans Araması" else null,
            connectTime = d.connectTimeMillis
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
        override fun onParentChanged(call: Call, parent: Call?) = publish()
        override fun onChildrenChanged(call: Call, children: List<Call>) = publish()
    }

    fun attach(c: Call) {
        _ended.value = null
        if (calls.none { it === c }) {
            calls.add(c)
            c.registerCallback(callback)
        }
        publish()
    }

    @Suppress("UNUSED_PARAMETER")
    fun detach(c: Call, remaining: Call?) {
        c.unregisterCallback(callback)
        calls.removeAll { it === c }
        publish()
    }

    fun clear() {
        calls.forEach { it.unregisterCallback(callback) }
        calls.clear()
        service = null
        publish()
    }

    private fun publish() {
        val p = primary()
        val o = secondary(p)
        _ui.value = p?.let { toUi(it) }
        _other.value = o?.let { toUi(it) }
        _canMerge.value = p != null && o != null && (
            p.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) ||
                o.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)
            )
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
        primary()?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Çağrı beklerken: mevcut aramayı beklemeye alıp gelen aramayı yanıtlar. */
    fun holdAndAnswer() {
        val p = primary() ?: return
        secondary(p)?.takeIf { st(it) == Call.STATE_ACTIVE }?.hold()
        p.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Çağrı beklerken: mevcut aramayı bitirip gelen aramayı yanıtlar. */
    fun endAndAnswer() {
        val p = primary() ?: return
        secondary(p)?.takeIf { st(it) == Call.STATE_ACTIVE || st(it) == Call.STATE_HOLDING }?.disconnect()
        p.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        val c = primary() ?: return
        if (st(c) == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }

    fun hangup() {
        primary()?.disconnect()
    }

    fun dtmfStart(c: Char) {
        primary()?.playDtmfTone(c)
    }

    fun dtmfStop() {
        primary()?.stopDtmfTone()
    }

    fun toggleHold() {
        val c = primary() ?: return
        if (st(c) == Call.STATE_HOLDING) c.unhold() else c.hold()
    }

    /** İki arama arasında geçiş: aktif olanı beklemeye alır, beklemedekini geri açar. */
    fun swap() {
        val p = primary() ?: return
        val o = secondary(p) ?: return
        if (st(o) != Call.STATE_HOLDING) return
        if (st(p) == Call.STATE_ACTIVE) p.hold()
        o.unhold()
    }

    /** İki aramayı konferansta birleştirir. */
    fun merge() {
        val p = primary() ?: return
        val o = secondary(p) ?: return
        if (p.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) p.mergeConference()
        else if (o.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) o.mergeConference()
        else p.conference(o)
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
