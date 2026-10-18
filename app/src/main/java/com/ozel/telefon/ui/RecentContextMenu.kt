package com.ozel.telefon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozel.telefon.formatNumber
import com.ozel.telefon.ui.theme.IOS
import kotlin.math.roundToInt

/**
 * Son Aramalar'da bir satıra uzun basınca açılan iOS tarzı menü.
 * Arkadaki ekranın bulanıklaştırılması MainActivity'de yapılır; burada sadece karartma + menü var.
 */
@Composable
fun RecentContextMenu(
    target: MenuTarget?,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onCall: (String) -> Unit,
    onVideo: () -> Unit
) {
    // Çıkış animasyonu sırasında hedef kaybolmasın diye son hedef saklanır.
    val holder = remember { arrayOfNulls<MenuTarget>(1) }
    if (target != null) holder[0] = target
    val t = holder[0]

    BackHandler(enabled = target != null) { onDismiss() }

    AnimatedVisibility(
        visible = target != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150))
    ) {
        if (t != null) {
            val density = LocalDensity.current
            val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
            val blockH = with(density) { (46 + 8 + 3 * 44 + 8).dp.toPx() }
            val bottomReserve = with(density) { 110.dp.toPx() }
            val topReserve = with(density) { 40.dp.toPx() }
            val top = t.rowTop.coerceIn(topReserve, (screenH - blockH - bottomReserve).coerceAtLeast(topReserve))

            val scale by animateFloatAsState(
                targetValue = if (target != null) 1f else 0.9f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                label = "menuScale"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            ) {
                Column(
                    Modifier
                        .offset { IntOffset(with(density) { 10.dp.roundToPx() }, top.roundToInt()) }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        // menü içine dokunmak kapatmasın
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    // Kişi kartı
                    Row(
                        Modifier
                            .width(290.dp)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(t.number, t.name, 32.dp, 13.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            t.name ?: formatNumber(t.number).ifBlank { "Bilinmeyen" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Menü
                    Column(
                        Modifier
                            .width(240.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        MenuItem("Mesaj Gönder", F7.BubbleLeft) { onMessage(t.number) }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                        MenuItem("Ara", F7.Phone) { onCall(t.number) }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                        MenuItem("Görüntülü", F7.Videocam) { onVideo() }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .iosClickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(F7.ChevronRight, null, tint = IOS.Gray, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
    }
}
