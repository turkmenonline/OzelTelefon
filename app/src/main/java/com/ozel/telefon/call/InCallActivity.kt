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
import com.ozel.telefon.lookupContact
import com.ozel.telefon.ui.theme.OzelTelefonTheme
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
                val context = LocalContext.current
                val current = ui

                val endedState by CallState.ended.collectAsState()
                val e = endedState

                // Çağrı bitince (meşgul ekranı yoksa) hemen kapat; solma animasyonu pencerede oynar.
                LaunchedEffect(current == null && e == null) {
                    if (current == null && e == null) {
                        finish()
                    }
                }

                // Kapanırken ekran boşalıp siyah kalmasın diye son çağrı ekranı görünür tutulur.
                val last = remember { arrayOfNulls<CallUi>(1) }
                if (current != null) last[0] = current
                if (e != null) last[0] = null
                val shown = current ?: last[0]

                if (e != null) {
                    val endedInfo by produceState<ContactInfo?>(null, e.number) {
                        value = withContext(Dispatchers.IO) { lookupContact(context, e.number) }
                    }
                    FadeIn(250) {
                        EndedScreen(
                            title = e.displayName ?: endedInfo?.name
                                ?: formatNumber(e.number).ifBlank { "Bilinmeyen" },
                            reason = e.reason,
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
                    FadeIn(350) {
                    InCallScreen(
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
                        onDtmfStop = { CallState.dtmfStop() }
                    )
                    }
                }
            }
        }
    }
}
