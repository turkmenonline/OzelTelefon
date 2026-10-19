package com.ozel.telefon.call

import com.ozel.telefon.ui.F7

import android.telecom.Call as TCall
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ozel.telefon.ContactInfo
import com.ozel.telefon.formatNumber
import com.ozel.telefon.ui.theme.IOS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// iOS 26 arama ekranı: koyu teal-mavi gradyan zemin, yarı saydam cam düğmeler.
private val glass = Color.White.copy(alpha = 0.14f)
private val glassStrong = Color.White.copy(alpha = 0.22f)
private val cyan = Color(0xFF8ED8F2)
private val endRed = Color(0xFFC40F14)

private val dtmfRows = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to "")
)

private fun nameSize(title: String): Int = when {
    title.length <= 12 -> 34
    title.length <= 16 -> 30
    else -> 26
}

val DefaultCallBg = listOf(Color(0xFF4F7F7C), Color(0xFF0E4E6E), Color(0xFF07525A))

private val namePalette = listOf(
    listOf(Color(0xFF8A6BB5), Color(0xFF5B4590), Color(0xFF3F3070)),   // mor
    listOf(Color(0xFF4F7F7C), Color(0xFF0E4E6E), Color(0xFF07525A)),   // teal
    listOf(Color(0xFF5C74B8), Color(0xFF34488F), Color(0xFF26346B)),   // indigo
    listOf(Color(0xFFB8647A), Color(0xFF8A3A57), Color(0xFF63263F)),   // gül
    listOf(Color(0xFFB58A55), Color(0xFF8A5E2E), Color(0xFF5F3F1C)),   // amber
    listOf(Color(0xFF5F9B6A), Color(0xFF2F6B45), Color(0xFF1F4A31))    // yeşil
)

/**
 * iOS gibi: arama zemini kişiye göre değişir. Fotoğraf varsa ortalama renginden,
 * yoksa isimden seçilen sabit bir renkten türetilir; kayıtsız numara varsayılan teal.
 */
fun callGradient(name: String?, photo: ImageBitmap?): List<Color> {
    if (photo != null) {
        val avg = runCatching {
            val b = android.graphics.Bitmap.createScaledBitmap(photo.asAndroidBitmap(), 8, 8, true)
            var r = 0L; var g = 0L; var bl = 0L
            for (x in 0 until 8) for (y in 0 until 8) {
                val c = b.getPixel(x, y)
                r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); bl += android.graphics.Color.blue(c)
            }
            floatArrayOf(0f, 0f, 0f).also {
                android.graphics.Color.RGBToHSV((r / 64).toInt(), (g / 64).toInt(), (bl / 64).toInt(), it)
            }
        }.getOrNull()
        if (avg != null) {
            val h = avg[0]
            val sat = avg[1].coerceIn(0.35f, 0.75f)
            fun hsv(hh: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf((hh + 360f) % 360f, sat, v)))
            return listOf(hsv(h, 0.62f), hsv(h + 8f, 0.42f), hsv(h - 8f, 0.34f))
        }
    }
    if (name.isNullOrBlank()) return DefaultCallBg
    return namePalette[Math.floorMod(name.hashCode(), namePalette.size)]
}

@Composable
private fun CallBackground(colors: List<Color> = DefaultCallBg, content: @Composable () -> Unit) {
    val c0 by animateColorAsState(colors[0], tween(600), label = "bg0")
    val c1 by animateColorAsState(colors[1], tween(600), label = "bg1")
    val c2 by animateColorAsState(colors[2], tween(600), label = "bg2")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(c0, c1, c2)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33000000))))
    ) { content() }
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
    onDtmfStop: () -> Unit,
    onMessage: () -> Unit = {},
    bg: List<Color> = DefaultCallBg,
    ending: Boolean = false,
    other: CallUi? = null,
    otherTitle: String = "",
    canMerge: Boolean = false,
    onSwap: () -> Unit = {},
    onMerge: () -> Unit = {},
    onHoldAnswer: () -> Unit = {},
    onEndAnswer: () -> Unit = {}
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
        TCall.STATE_DISCONNECTING, TCall.STATE_DISCONNECTED -> "Arama Bitti"
        else -> ""
    }.let { if (ending) "Arama Bitti" else it }

    // Arama bitince iOS gibi her şey soluklaşır ve dokunuşlar kapanır.
    val fade by animateFloatAsState(if (ending) 0.35f else 1f, tween(500), label = "endFade")

    CallBackground(bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(fade)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (showKeypad) 16.dp else 56.dp))
            Text(
                text = status,
                color = cyan,
                fontSize = 20.sp,
                letterSpacing = (-0.3).sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            val size = if (showKeypad) 24 else nameSize(title)
            Text(
                text = title,
                color = Color.White,
                fontSize = size.sp,
                lineHeight = (size * 1.12f).sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
            if (other != null && !ringing && !showKeypad) {
                Spacer(Modifier.height(14.dp))
                OtherCallPill(
                    label = (if (other.state == TCall.STATE_HOLDING) "Beklemede: " else "") + otherTitle,
                    onClick = onSwap
                )
            }
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
                ringing && other != null -> {
                    WaitingOption("Beklet ve Kabul Et", F7.PauseFill, glassStrong, onHoldAnswer)
                    Spacer(Modifier.height(10.dp))
                    WaitingOption("Bitir ve Kabul Et", F7.PhoneDownFill, endRed.copy(alpha = 0.85f), onEndAnswer)
                    Spacer(Modifier.height(10.dp))
                    WaitingOption("Reddet", F7.Xmark, glass, onReject)
                    Spacer(Modifier.height(8.dp))
                }

                ringing -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        SmallAction(F7.BubbleLeftFill, "Mesaj", onMessage)
                        SmallAction(F7.Recordingtape, "Sesli Mesaj", onReject)
                    }
                    Spacer(Modifier.height(28.dp))
                    SlideToAnswer(onAnswer)
                    Spacer(Modifier.height(8.dp))
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
                        RoundButton(endRed, F7.PhoneDownFill, 70, onHangup)
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
                            GridButton(F7.Speaker3Fill, "Hoparlör", active = audio.speaker, onClick = onSpeaker)
                        }
                        Cell {
                            if (other != null) {
                                GridButton(F7.PersonFill, "Birleştir", enabled = canMerge, onClick = onMerge)
                            } else {
                                GridButton(F7.VideocamFill, "FaceTime", enabled = false, onClick = {})
                            }
                        }
                        Cell {
                            GridButton(
                                if (audio.muted) F7.MicSlashFill else F7.MicFill,
                                "Sessiz",
                                active = audio.muted,
                                onClick = onMute
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Cell {
                            MoreButton(
                                canHold = canHold,
                                holding = ui.state == TCall.STATE_HOLDING,
                                onHold = onHold
                            )
                        }
                        Cell {
                            LabeledRound(endRed, F7.PhoneDownFill, "Bitir", onHangup)
                        }
                        Cell {
                            GridButton(F7.CircleGrid3x3Fill, "Klavye", onClick = { showKeypad = true })
                        }
                    }
                }
            }
            Spacer(Modifier.height(52.dp))
        }
        if (ending) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            )
        }
    }
}

/** Aramalar arası geçiş: diğer çağrının adını gösteren küçük cam hap. */
@Composable
private fun OtherCallPill(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(glassStrong)
            .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(F7.PhoneFill, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 15.sp, maxLines = 1, fontWeight = FontWeight.Medium)
    }
}

/** Çağrı bekleme seçeneği: tam genişlik cam hap. */
@Composable
private fun WaitingOption(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(color)
            .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
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
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.46f).dp))
    }
}

@Composable
private fun LabeledRound(
    color: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Int = 74
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(color, icon, size, onClick)
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

/** Gelen aramada küçük cam düğme: Mesaj ve Sesli Mesaj. */
@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(glassStrong),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

/** "Yanıtlamak için kaydır" çubuğu (iOS 26 kilit ekranı arama stili). */
@Composable
private fun SlideToAnswer(onAnswer: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val knob = 64.dp
    val inset = 4.dp
    var widthPx by remember { mutableIntStateOf(0) }
    val dragX = remember { Animatable(0f) }
    val maxX = with(density) { (widthPx - knob.toPx() - 2 * inset.toPx()).coerceAtLeast(1f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(CircleShape)
            .background(glass)
            .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
            .onSizeChanged { widthPx = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(inset)
                .offset { IntOffset(dragX.value.roundToInt(), 0) }
                .size(knob)
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(maxX) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (dragX.value > maxX * 0.75f) {
                                    dragX.animateTo(maxX)
                                    onAnswer()
                                } else {
                                    dragX.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { dragX.animateTo(0f) } }
                    ) { change, amount ->
                        change.consume()
                        scope.launch { dragX.snapTo((dragX.value + amount).coerceIn(0f, maxX)) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(F7.PhoneFill, contentDescription = "Yanıtla", tint = IOS.Green, modifier = Modifier.size(30.dp))
        }
    }
}

/** iOS çağrı ekranındaki yarı saydam daire düğme (içeriği dışarıdan verilir). */
@Composable
private fun GridButtonBase(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else glass)
                .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f)), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { content() }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.6f), fontSize = 14.sp)
    }
}

@Composable
private fun GridButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    GridButtonBase(label, active, enabled, onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = (if (active) Color.Black else Color.White).copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(32.dp)
        )
    }
}

/** "Daha Fazla": şimdilik sadece Beklet / Beklemeden Al menüsü. */
@Composable
private fun MoreButton(canHold: Boolean, holding: Boolean, onHold: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val dy = with(LocalDensity.current) { 84.dp.roundToPx() }
    Box {
        GridButtonBase("Daha Fazla", active = false, enabled = canHold, onClick = { open = true }) {
            Canvas(Modifier.size(30.dp)) {
                val r = 2.6.dp.toPx()
                val c = Color.White.copy(alpha = if (canHold) 1f else 0.4f)
                for (i in 0..2) {
                    drawCircle(c, r, Offset(size.width * (0.2f + 0.3f * i), size.height / 2f))
                }
            }
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, -dy),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xE61C3A44))
                        .clickable {
                            open = false
                            onHold()
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(F7.PauseFill, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (holding) "Beklemeden Al" else "Beklet",
                        color = Color.White,
                        fontSize = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DtmfKey(ch: Char, letters: String, onDown: () -> Unit, onUp: () -> Unit) {
    Box(
        modifier = Modifier
            .size(66.dp)
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
fun EndedScreen(
    title: String,
    reason: String,
    onDismiss: () -> Unit,
    onRedial: () -> Unit,
    bg: List<Color> = DefaultCallBg
) {
    CallBackground(bg) {
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
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 24.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            val size = if (title.length <= 10) 40 else nameSize(title)
            Text(
                text = title,
                color = Color.White,
                fontSize = size.sp,
                lineHeight = (size * 1.1f).sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF8A9199))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            F7.Xmark,
                            contentDescription = "Vazgeç",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Vazgeç", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RoundButton(Color(0xFF3E8E41), F7.PhoneFill, 68, onRedial)
                    Spacer(Modifier.height(10.dp))
                    Text("Geri Ara", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(58.dp))
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
