package com.ozel.telefon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
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
private fun TabBarRow(
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

    var anyPressed by remember { mutableStateOf(false) }
    var barPos by remember { mutableStateOf(Offset.Zero) }
    val lensOn = lensShaderActive()
    var dragging by remember { mutableStateOf(false) }
    val lens by animateFloatAsState(
        if (anyPressed || dragging) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "lens"
    )
    // Hap konumu (sekme biriminde). Parmakla sürüklenir, bırakınca en yakın sekmeye yaylanır.
    val pillAnim = remember { Animatable(selected.toFloat()) }
    val pillSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 380f)
    LaunchedEffect(selected) { if (!dragging) pillAnim.animateTo(selected.toFloat(), pillSpring) }
    val pill = pillAnim.value // sadece 'nearest' için
    val scope = rememberCoroutineScope()
    val currentSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val nearest = pill.roundToInt().coerceIn(0, labels.size - 1)
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
                .onGloballyPositioned { barPos = it.positionInRoot() }
                .graphicsLayer {
                    val sc = 1f + 0.035f * lens
                    scaleX = sc
                    scaleY = sc
                }
                .shadow(10.dp, CircleShape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        ) {
            val tabW = (maxWidth - 8.dp) / labels.size
            val tabPx = with(LocalDensity.current) { tabW.toPx() }
            val padPx = with(LocalDensity.current) { 4.dp.toPx() }
            val mxPx = with(LocalDensity.current) { 28.dp.toPx() }
            val myPx = with(LocalDensity.current) { 20.dp.toPx() }
            val growXPx = with(LocalDensity.current) { 7.dp.toPx() }
            val moveXPx = with(LocalDensity.current) { 14.dp.toPx() }
            val growYPx = with(LocalDensity.current) { 9.dp.toPx() }
            // Cam gövde (kırpılır). Mercek bunun dışına taşabilsin diye ayrı katman.
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .glassBackdrop()
                    .background(glass)
                    .border(BorderStroke(0.5.dp, border), CircleShape)
            )
            // Seçili hap: gerçek kırılmalı mercek. Şişme shader içinde çizilir; çubuğun dışına taşabilir.
            Box(Modifier.fillMaxSize().padding(4.dp)) {
                if (lensOn) {
                    Box(
                        Modifier
                            .layout { measurable, c ->
                                val w = (tabPx + 2f * mxPx).roundToInt()
                                val h = (c.maxHeight + 2f * myPx).roundToInt()
                                val pl = measurable.measure(Constraints.fixed(w, h))
                                layout(0, 0) {
                                    pl.place((pillAnim.value * tabPx - mxPx).roundToInt(), (-myPx).roundToInt())
                                }
                            }
                            .liquidLens(
                                amount = { 0.45f + 0.55f * lens },
                                expand = {
                                    val moving = if (dragging) 0f else kotlin.math.abs(pillAnim.value - selected).coerceIn(0f, 1.5f)
                                    Offset(
                                        (growXPx * lens + moveXPx * moving).coerceAtMost(mxPx - 10f),
                                        growYPx * lens
                                    )
                                },
                                margin = Offset(mxPx, myPx),
                                origin = {
                                    Offset(
                                        barPos.x + padPx + pillAnim.value * tabPx - mxPx,
                                        barPos.y + padPx - myPx
                                    )
                                }
                            )
                    )
                } else {
                    Box(
                        Modifier
                            .offset { IntOffset((pillAnim.value * tabPx).roundToInt(), 0) }
                            .width(tabW)
                            .fillMaxHeight()
                            .graphicsLayer {
                                val moving = if (dragging) 0f else kotlin.math.abs(pillAnim.value - selected).coerceIn(0f, 1.5f)
                                scaleX = 1f + 0.28f * moving + 0.14f * lens
                                scaleY = 1f + 0.06f * moving + 0.30f * lens
                            }
                            .clip(RoundedCornerShape(27.dp))
                            .background(highlight)
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .pointerInput(tabPx, labels.size) {
                        val last = (labels.size - 1).toFloat()
                        fun finish() {
                            dragging = false
                            val target = pillAnim.value.roundToInt().coerceIn(0, labels.size - 1)
                            scope.launch { pillAnim.animateTo(target.toFloat(), pillSpring) }
                            if (target != currentSelected) currentSelect(target)
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                scope.launch {
                                    pillAnim.snapTo((pillAnim.value + dx / tabPx).coerceIn(0f, last))
                                }
                            }
                        )
                    }
            ) {
                labels.forEachIndexed { i, label ->
                    val active = i == (if (dragging) nearest else selected)
                    val tint by animateColorAsState(
                        if (active) IOS.Blue else MaterialTheme.colorScheme.onBackground,
                        tween(180),
                        label = "tabTint"
                    )
                    val src = remember { MutableInteractionSource() }
                    val pressed by src.collectIsPressedAsState()
                    val sc by pressScale(pressed, 1.1f)
                    LaunchedEffect(pressed) { anyPressed = pressed }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                // Mercek altındaki ikon büyür (iOS 26): hapa yakınlık * mercek gücü.
                                val near = (1f - kotlin.math.abs(pillAnim.value - i)).coerceIn(0f, 1f)
                                val zoom = 1f + 0.22f * lens * near
                                scaleX = sc * zoom
                                scaleY = sc * zoom
                            }
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

/** Arama modu: alttaki büyüteç düğmesi açar; Aramalar ve Kişiler listeleri buradaki metne göre süzülür. */
object SearchMode {
    var active by mutableStateOf(false)
    var query by mutableStateOf("")
    fun open() { active = true }
    fun close() { active = false; query = "" }
}

/**
 * Alt çubuk: normalde sekmeler, arama modunda arama alanı + kapat düğmesi (iOS 26).
 * Geçişte sekme hapı sola doğru küçülüp solar, arama alanı sağdaki büyüteçten yayılarak açılır.
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
    val active = SearchMode.active
    val t = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active) {
        t.animateTo(if (active) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 320f))
    }
    val v = t.value
    Box(
        modifier = if (active) modifier.imePadding() else modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        if (v < 0.99f) {
            Box(
                Modifier.graphicsLayer {
                    alpha = (1f - v * 1.8f).coerceIn(0f, 1f)
                    scaleX = 1f - 0.3f * v
                    scaleY = 1f - 0.1f * v
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
            ) {
                TabBarRow(labels, icons, selected, onSelect, onSearch, Modifier)
            }
        }
        if (active || v > 0.01f) {
            Box(
                Modifier.graphicsLayer {
                    alpha = ((v - 0.15f) * 1.6f).coerceIn(0f, 1f)
                    scaleX = 0.45f + 0.55f * v
                    scaleY = 0.85f + 0.15f * v
                    transformOrigin = TransformOrigin(1f, 0.5f)
                }
            ) {
                SearchBottomBar(Modifier, active)
            }
        }
    }
}

@Composable
private fun SearchBottomBar(modifier: Modifier, active: Boolean) {
    val glass = glassTint()
    val border = glassBorderColor()
    val shadowColor = Color(0x26000000)
    val fg = MaterialTheme.colorScheme.onBackground
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(active) {
        if (active) focus.requestFocus() else focusManager.clearFocus()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .shadow(10.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                .clip(CircleShape)
                .glassBackdrop()
                .background(glass)
                .border(BorderStroke(0.5.dp, border), CircleShape)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(F7.Search, null, tint = IOS.Gray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (SearchMode.query.isEmpty()) Text("Ara", color = IOS.Gray, fontSize = 17.sp)
                BasicTextField(
                    value = SearchMode.query,
                    onValueChange = { SearchMode.query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 17.sp),
                    cursorBrush = SolidColor(IOS.Blue),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            Icon(F7.MicFill, null, tint = IOS.Gray, modifier = Modifier.size(18.dp))
        }
        GlassCircleButton(onClick = { SearchMode.close() }, size = 52.dp) {
            Icon(F7.Xmark, "Kapat", tint = fg, modifier = Modifier.size(18.dp))
        }
    }
}
