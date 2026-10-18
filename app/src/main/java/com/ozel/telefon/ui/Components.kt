package com.ozel.telefon.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozel.telefon.loadContactPhoto
import com.ozel.telefon.peekPhoto
import com.ozel.telefon.photoPossible
import com.ozel.telefon.ui.theme.IOS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun LargeTitle(text: String) {
    Text(
        text = text,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
        ),
        cursorBrush = SolidColor(IOS.Blue),
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = IOS.Gray, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = IOS.Gray, fontSize = 17.sp)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = "Temizle",
                        tint = IOS.Gray,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onValueChange("") }
                    )
                }
            }
        }
    )
}

/** iOS tarzı "Tümü | Cevapsız" segmenti. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) MaterialTheme.colorScheme.background else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

fun initialsOf(name: String?): String? {
    val words = name?.trim()?.split(" ")?.filter { it.isNotEmpty() && it.first().isLetter() }
    if (words.isNullOrEmpty()) return null
    val tr = Locale("tr", "TR")
    return words.take(2).joinToString("") { it.first().toString().uppercase(tr) }
}

private val avatarGradient = listOf(Color(0xFFA6AAB4), Color(0xFF858891))

/** Gri-gradyan daire + baş harfler (iOS "AT" avatarı). */
@Composable
fun InitialsAvatar(name: String?, size: Dp, fontSize: TextUnit) {
    val initials = initialsOf(name)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(avatarGradient)),
        contentAlignment = Alignment.Center
    ) {
        if (initials != null) {
            Text(initials, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Medium)
        } else {
            Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** Rehberdeki fotoğrafı gösterir, yoksa baş harfler. */
@Composable
fun ContactAvatar(number: String, name: String?, size: Dp, fontSize: TextUnit = 22.sp) {
    val context = LocalContext.current
    val version = ListData.version
    // Önbellekte varsa ilk karede hazır gelir: harf avatarı -> foto "çakılması" ve IO işi olmaz.
    val cached = remember(number, version) { peekPhoto(number, 200) }
    val photo by produceState<ImageBitmap?>(cached, number, version) {
        if (value == null && photoPossible(number)) {
            value = withContext(Dispatchers.IO) { loadContactPhoto(context, number, false, 200) }
        }
    }
    val p = photo
    if (p != null) {
        Image(
            bitmap = p,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        InitialsAvatar(name, size, fontSize)
    }
}

/**
 * iOS tarzı dokunma: dalga (ripple) yok, basılı tutulan satır gri vurgulanır.
 * onLongClick verilirse uzun basma da algılanır.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.iosClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val highlight = MaterialTheme.colorScheme.surfaceVariant
    this
        .background(if (pressed && enabled) highlight else Color.Transparent)
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick
        )
}
