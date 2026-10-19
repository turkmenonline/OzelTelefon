package com.ozel.telefon.call

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.ozel.telefon.ContactInfo
import com.ozel.telefon.R
import com.ozel.telefon.formatNumber
import com.ozel.telefon.placeCall
import com.ozel.telefon.sendSms
import com.ozel.telefon.lookupContact
import com.ozel.telefon.ui.theme.OzelTelefonTheme
import android.telecom.Call as TCall
import com.ozel.telefon.loadContactPhoto
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InCallActivity : ComponentActivity() {

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        // Android 14+ için geçiş onCreate'te overrideActivityTransition ile ayarlandı.
        if (Build.VERSION.SDK_INT < 34) {
            overridePendingTransition(R.anim.hold, R.anim.fade_out)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            // Samsung/Android 14'te eski overridePendingTransition yok sayılıyor; bu yöntem geçerli.
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.hold)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.hold, R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.hold)
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // Fotoğraf üstünde okunsun diye durum/gezinme çubuğu simgeleri hep beyaz.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        setContent {
            OzelTelefonTheme(dark = true) {
                val ui by CallState.ui.collectAsState()
                val audio by CallState.audio.collectAsState()
                val other by CallState.other.collectAsState()
                val canMerge by CallState.canMerge.collectAsState()
                val context = LocalContext.current
                val current = ui

                val endedState by CallState.ended.collectAsState()
                val e = endedState

                // Çağrı bitince (meşgul ekranı yoksa) hemen kapat; solma animasyonu pencerede oynar.
                // Bağlanmış arama normal bitince "Arama Bitti" ile soluklaşıp kendiliğinden kapanır.
                val wasConnected = remember { booleanArrayOf(false) }
                if (current != null &&
                    (current.state == TCall.STATE_ACTIVE || current.state == TCall.STATE_HOLDING)
                ) wasConnected[0] = true

                LaunchedEffect(current == null && e == null) {
                    if (current == null && e == null) {
                        if (wasConnected[0]) delay(1300)
                        finish()
                    }
                }

                // Kapanırken ekran boşalıp siyah kalmasın diye son çağrı ekranı görünür tutulur.
                val last = remember { arrayOfNulls<CallUi>(1) }
                if (current != null) last[0] = current
                if (e != null) last[0] = null
                val shown = current ?: last[0]

                if (e != null) {
                    val endedBg by produceState(DefaultCallBg, e.number) {
                        value = withContext(Dispatchers.IO) {
                            callGradient(
                                e.displayName ?: lookupContact(context, e.number)?.name,
                                loadContactPhoto(context, e.number, false, 200)
                            ).takeIf { lookupContact(context, e.number)?.name != null || e.displayName != null }
                                ?: DefaultCallBg
                        }
                    }
                    val endedInfo by produceState<ContactInfo?>(null, e.number) {
                        value = withContext(Dispatchers.IO) { lookupContact(context, e.number) }
                    }
                    FadeIn(250) {
                        EndedScreen(
                            title = e.displayName ?: endedInfo?.name
                                ?: formatNumber(e.number).ifBlank { "Bilinmeyen" },
                            reason = e.reason,
                            bg = endedBg,
                            onDismiss = { CallState.dismissEnded() },
                            onRedial = {
                                val n = e.number
                                CallState.dismissEnded()
                                placeCall(context, n)
                            }
                        )
                    }
                } else if (shown != null) {
                    val info by produceState<ContactInfo?>(null, shown.number) {
                        value = withContext(Dispatchers.IO) { lookupContact(context, shown.number) }
                    }
                    val otherCall = other
                    val otherTitle by produceState("", otherCall?.number, otherCall?.displayName) {
                        value = if (otherCall == null) "" else withContext(Dispatchers.IO) {
                            otherCall.displayName ?: lookupContact(context, otherCall.number)?.name
                                ?: formatNumber(otherCall.number).ifBlank { "Bilinmeyen" }
                        }
                    }
                    val bg by produceState(DefaultCallBg, shown.number) {
                        value = withContext(Dispatchers.IO) {
                            val name = lookupContact(context, shown.number)?.name ?: shown.displayName
                            if (name == null) DefaultCallBg
                            else callGradient(name, loadContactPhoto(context, shown.number, false, 200))
                        }
                    }
                    FadeIn(350) {
                    InCallScreen(
                        bg = bg,
                        ending = current == null,
                        other = otherCall,
                        otherTitle = otherTitle,
                        canMerge = canMerge,
                        onSwap = { CallState.swap() },
                        onMerge = { CallState.merge() },
                        onHoldAnswer = { CallState.holdAndAnswer() },
                        onEndAnswer = { CallState.endAndAnswer() },
                        ui = shown,
                        info = info,
                        audio = audio,
                        onAnswer = { CallState.answer() },
                        onReject = { CallState.reject() },
                        onHangup = { CallState.hangup() },
                        onMute = { CallState.toggleMute() },
                        onSpeaker = { CallState.toggleSpeaker() },
                        onHold = { CallState.toggleHold() },
                        onDtmfStart = { CallState.dtmfStart(it) },
                        onDtmfStop = { CallState.dtmfStop() },
                        onMessage = {
                            CallState.reject()
                            sendSms(context, shown.number)
                        }
                    )
                    }
                }
            }
        }
    }
}
