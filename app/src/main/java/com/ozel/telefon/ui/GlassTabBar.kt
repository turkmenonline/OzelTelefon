package com.ozel.telefon.ui

import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozel.telefon.ui.theme.IOS

/** Cam renkleri: açık/koyu temaya göre yarı saydam yüzey. */
@Composable
fun glassSurfaceColor(): Color =
    if (isSystemInDarkTheme()) Color(0xE61C1C1E) else Color(0xE6FFFFFF)

@Composable
fun glassBorderColor(): Color =
    if (isSystemInDarkTheme()) Color(0x33FFFFFF) else Color(0x14000000)

/**
 * iOS 26 "Unified" alt çubuğu: yüzen hap şeklinde sekmeler + yanında yuvarlak arama düğmesi.
 * Seçili sekmenin arkasında yumuşak bir hap vurgusu olur.
 */
@Composable
fun GlassTabBar(
    labels: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = glassTint()
    val border = glassBorderColor()
    val shadowColor = Color(0x26000000)
    val highlight = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)

    val pill by animateFloatAsState(
        selected.toFloat(),
        spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "pill"
    )
    val searchSrc = remember { MutableInteractionSource() }
    val searchPressed by searchSrc.collectIsPressedAsState()
    val searchScale by pressScale(searchPressed, 0.9f)
    val searchFill by pressFill(searchPressed)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(62.dp)
                .shadow(10.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                .clip(CircleShape)
                .glassBackdrop()
                .background(glass)
                .border(BorderStroke(0.5.dp, border), CircleShape)
                .padding(4.dp)
        ) {
            val tabW = maxWidth / labels.size
            // Seçili hap sekmeler arasında yaylı kayar (iOS 26 cam çubuk).
            Box(
                Modifier
                    .offset { IntOffset((pill * tabW.toPx()).roundToInt(), 0) }
                    .width(tabW)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(27.dp))
                    .background(highlight)
            )
            Row(Modifier.fillMaxSize()) {
                labels.forEachIndexed { i, label ->
                    val active = i == selected
                    val tint by animateColorAsState(
                        if (active) IOS.Blue else MaterialTheme.colorScheme.onBackground,
                        tween(180),
                        label = "tabTint"
                    )
                    val src = remember { MutableInteractionSource() }
                    val pressed by src.collectIsPressedAsState()
                    val sc by pressScale(pressed, 0.9f)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer { scaleX = sc; scaleY = sc }
                            .clip(RoundedCornerShape(27.dp))
                            .clickable(interactionSource = src, indication = null) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icons[i], contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
                        Text(
                            label,
                            color = tint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = searchScale; scaleY = searchScale }
                .size(62.dp)
                .shadow(10.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                .clip(CircleShape)
                .glassBackdrop()
                .background(glass)
                .background(searchFill)
                .border(BorderStroke(0.5.dp, border), CircleShape)
                .clickable(interactionSource = searchSrc, indication = null, onClick = onSearch),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                F7.Search,
                contentDescription = "Ara",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
