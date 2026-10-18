package com.ozel.telefon.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val softShadow = Color(0x22000000)

/** iOS cam düğmeler basılınca hafifçe küçülür (yaylı geri dönüş). */
@Composable
fun pressScale(pressed: Boolean, to: Float = 0.92f): State<Float> =
    animateFloatAsState(
        if (pressed) to else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "pressScale"
    )

/** Basılıyken düğmenin üstüne binen açık vurgu. */
@Composable
fun pressFill(pressed: Boolean): State<Color> =
    animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f) else Color.Transparent,
        tween(if (pressed) 60 else 220),
        label = "pressFill"
    )

/**
 * Popup/menü açılışı: belirtilen köşeden (düğmenin olduğu yer) büyüyerek ve belirerek gelir.
 * iOS 26'daki gibi yaylı; hafif taşıp yerine oturur.
 */
@Composable
fun PopupAppear(origin: TransformOrigin, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 450f)) }
    Box(
        Modifier.graphicsLayer {
            val sc = 0.55f + 0.45f * a.value
            scaleX = sc
            scaleY = sc
            alpha = (a.value * 1.6f).coerceIn(0f, 1f)
            transformOrigin = origin
        }
    ) { content() }
}

/** iOS 26 üst çubuktaki yuvarlak cam düğme (filtre, geri, + gibi). */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by pressScale(pressed)
    val fill by pressFill(pressed)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .size(size)
            .shadow(6.dp, CircleShape, ambientColor = softShadow, spotColor = softShadow)
            .clip(CircleShape)
            .background(glassSurfaceColor())
            .background(fill)
            .border(BorderStroke(0.5.dp, glassBorderColor()), CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** "Düzenle" gibi hap şeklinde cam düğme. */
@Composable
fun GlassPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by pressScale(pressed, 0.94f)
    val fill by pressFill(pressed)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .height(44.dp)
            .shadow(6.dp, CircleShape, ambientColor = softShadow, spotColor = softShadow)
            .clip(CircleShape)
            .background(glassSurfaceColor())
            .background(fill)
            .border(BorderStroke(0.5.dp, glassBorderColor()), CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Filtre simgesi: üç yatay çizgi, giderek kısalır. */
@Composable
fun FilterLinesIcon(color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val sw = 2.2.dp.toPx()
        val w = size.width
        val h = size.height
        val ys = listOf(h * 0.22f, h * 0.5f, h * 0.78f)
        val fractions = listOf(1f, 0.68f, 0.36f)
        for (i in 0..2) {
            val len = (w - sw) * fractions[i]
            val x0 = (w - len) / 2f
            drawLine(color, Offset(x0, ys[i]), Offset(x0 + len, ys[i]), sw, StrokeCap.Round)
        }
    }
}
