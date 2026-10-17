package com.ozel.telefon.call

import android.telecom.Call as TCall
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozel.telefon.ContactInfo
import com.ozel.telefon.formatNumber
import com.ozel.telefon.ui.theme.IOS
import kotlinx.coroutines.delay

private val glass = Color.White.copy(alpha = 0.22f)

private val dtmfRows = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to "")
)

private fun nameSize(title: String): Int = when {
    title.length <= 6 -> 64
    title.length <= 9 -> 52
    title.length <= 13 -> 40
    else -> 30
}

@Composable
fun InCallScreen(
    ui: CallUi,
    info: ContactInfo?,
    audio: AudioUi,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onHangup: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onHold: () -> Unit,
    onDtmfStart: (Char) -> Unit,
    onDtmfStop: () -> Unit
) {
    val ringing = ui.state == TCall.STATE_RINGING
    val title = ui.displayName ?: info?.name ?: formatNumber(ui.number).ifBlank { "Bilinmeyen" }
    val kind = info?.label.orEmpty()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    var showKeypad by remember { mutableStateOf(false) }
    var dtmf by remember { mutableStateOf("") }

    val status = when (ui.state) {
        TCall.STATE_RINGING -> if (kind.isBlank()) "Gelen arama" else "$kind gelen arama"
        TCall.STATE_DIALING, TCall.STATE_CONNECTING, TCall.STATE_PULLING_CALL ->
            if (kind.isBlank()) "aranıyor…" else "$kind aranıyor…"
        TCall.STATE_HOLDING -> "Beklemede"
        TCall.STATE_ACTIVE -> formatElapsed(if (ui.connectTime > 0) now - ui.connectTime else 0)
        TCall.STATE_DISCONNECTING, TCall.STATE_DISCONNECTED -> "Arama sonlandı"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (showKeypad) 24.dp else 64.dp))
            Text(
                text = status,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            val size = if (showKeypad) 28 else nameSize(title)
            Text(
                text = title,
                color = Color.White,
                fontSize = size.sp,
                lineHeight = (size * 1.08f).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
            if (showKeypad) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = dtmf,
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    modifier = Modifier.height(44.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            when {
                ringing -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LabeledRound(IOS.Red, Icons.Filled.CallEnd, "Reddet", onReject)
                        LabeledRound(IOS.Green, Icons.Filled.Call, "Kabul Et", onAnswer)
                    }
                }

                showKeypad -> {
                    dtmfRows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            row.forEach { (ch, letters) ->
                                DtmfKey(ch, letters, {
                                    dtmf += ch
                                    onDtmfStart(ch)
                                }, onDtmfStop)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Spacer(Modifier.width(88.dp))
                        Spacer(Modifier.width(22.dp))
                        RoundButton(IOS.Red, Icons.Filled.CallEnd, 72, onHangup)
                        Spacer(Modifier.width(22.dp))
                        Box(Modifier.width(88.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Gizle",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { showKeypad = false }
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                else -> {
                    val canHold = ui.state == TCall.STATE_ACTIVE || ui.state == TCall.STATE_HOLDING
                    Row(Modifier.fillMaxWidth()) {
                        Cell {
                            GridButton(Icons.Filled.VolumeUp, "Hoparlör", active = audio.speaker, onClick = onSpeaker)
                        }
                        Cell {
                            GridButton(Icons.Filled.Videocam, "FaceTime", enabled = false, onClick = {})
                        }
                        Cell {
                            GridButton(
                                if (audio.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                "Sessiz",
                                active = audio.muted,
                                onClick = onMute
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Cell {
                            if (canHold) {
                                GridButton(
                                    Icons.Filled.Pause, "Beklet",
                                    active = ui.state == TCall.STATE_HOLDING, onClick = onHold
                                )
                            } else {
                                GridButton(Icons.Filled.PersonAdd, "Ekle", enabled = false, onClick = {})
                            }
                        }
                        Cell {
                            LabeledRound(IOS.Red, Icons.Filled.CallEnd, "Bitir", onHangup)
                        }
                        Cell {
                            GridButton(Icons.Filled.Dialpad, "Klavye", onClick = { showKeypad = true })
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun RowScope.Cell(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { content() }
}

@Composable
private fun RoundButton(color: Color, icon: ImageVector, size: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.42f).dp))
    }
}

@Composable
private fun LabeledRound(
    color: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Int = 76
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(color, icon, size, onClick)
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

/** iOS çağrı ekranındaki yarı saydam gri daire düğme. */
@Composable
private fun GridButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else glass)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = (if (active) Color.Black else Color.White).copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.7f), fontSize = 14.sp)
    }
}

@Composable
private fun DtmfKey(ch: Char, letters: String, onDown: () -> Unit, onUp: () -> Unit) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(glass)
            .pointerInput(ch) {
                detectTapGestures(onPress = {
                    onDown()
                    tryAwaitRelease()
                    onUp()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ch.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            if (letters.isNotEmpty()) {
                Text(
                    letters,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

private fun formatElapsed(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Giden arama meşgul / başarısız olunca: Vazgeç + Geri Ara. */
@Composable
fun EndedScreen(title: String, reason: String, onDismiss: () -> Unit, onRedial: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Text(
                text = reason,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            val size = nameSize(title)
            Text(
                text = title,
                color = Color.White,
                fontSize = size.sp,
                lineHeight = (size * 1.08f).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(glass)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Vazgeç",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Vazgeç", color = Color.White, fontSize = 14.sp)
                }
                LabeledRound(IOS.Green, Icons.Filled.Call, "Geri Ara", onRedial)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** İlk görünürken siyahtan yumuşakça belirir (iOS'taki gibi solma geçişi). */
@Composable
fun FadeIn(durationMs: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs)) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = a.value }
    ) { content() }
}
