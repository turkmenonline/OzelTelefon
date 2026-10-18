package com.ozel.telefon.ui

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozel.telefon.formatNumber
import com.ozel.telefon.ui.theme.IOS

private val keyRows = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to "")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(initialNumber: String, onCall: (String) -> Unit) {
    var number by remember { mutableStateOf(initialNumber) }
    LaunchedEffect(initialNumber) {
        if (initialNumber.isNotBlank()) number = initialNumber
    }

    // iOS 26: numara yazılırken rehberde eşleşen kişi önerisi çıkar.
    val contacts = ListData.contacts
    val suggestion = remember(number, contacts) {
        val digits = number.filter { it.isDigit() }
        if (digits.length < 2) null
        else contacts.firstOrNull { c -> c.number.filter { it.isDigit() }.contains(digits) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Üst bölge: numara ve kişi önerisi (iOS 26'da numara ekranın üstünde durur).
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatNumber(number),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (suggestion != null) {
                    SuggestionChip(
                        name = suggestion.name,
                        number = suggestion.number,
                        onClick = { number = suggestion.number }
                    )
                }
            }
        }

        keyRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { (digit, letters) ->
                    DialKey(
                        digit = digit,
                        letters = letters,
                        onLongClick = if (digit == "0") ({ number += "+" }) else null,
                        onClick = { number += digit }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(78.dp))
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(IOS.Green)
                    .clickable { onCall(number) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    F7.PhoneFill,
                    contentDescription = "Ara",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Box(modifier = Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                if (number.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onLongClick = { number = "" },
                                onClick = { number = number.dropLast(1) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            F7.DeleteLeftFill,
                            contentDescription = "Sil",
                            tint = IOS.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SuggestionChip(name: String, number: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1F000000))
            .clip(shape)
            .background(glassSurfaceColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(F7.PersonCropCircleFill, null, tint = IOS.Gray, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            fontSize = 15.sp,
            color = IOS.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            formatNumber(number),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    digit: String,
    letters: String,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    // iOS 26: açık temada neredeyse beyaz, koyu temada siyaha yakın koyu gri.
    val keyColor = if (isSystemInDarkTheme()) Color(0xFF1F1F21) else Color(0xFFF3F3F6)
    // iOS: tuş basılınca anında aydınlanır, bırakınca yavaşça söner.
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pressedColor = if (isSystemInDarkTheme()) Color(0xFF4A4A4D) else Color(0xFFD2D2D8)
    val shownColor by animateColorAsState(
        if (pressed) pressedColor else keyColor,
        tween(if (pressed) 40 else 350),
        label = "keyPress"
    )
    Box(
        modifier = Modifier
            .size(78.dp)
            .shadow(5.dp, CircleShape, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
            .clip(CircleShape)
            .background(shownColor)
            .combinedClickable(interactionSource = src, indication = null, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
