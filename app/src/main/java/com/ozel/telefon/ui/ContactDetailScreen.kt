package com.ozel.telefon.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import com.ozel.telefon.ContactDetail
import com.ozel.telefon.PhoneEntry
import com.ozel.telefon.decodeSquareBitmap
import com.ozel.telefon.deleteContact
import com.ozel.telefon.formatNumber
import com.ozel.telefon.loadContactDetail
import com.ozel.telefon.loadContactPhoto
import com.ozel.telefon.saveContact
import com.ozel.telefon.ui.theme.IOS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailTarget(val number: String, val startEditing: Boolean = false)

private class Loaded(val detail: ContactDetail?)

/** iOS tarzı kişi kartı + düzenleme ekranı. */
@Composable
fun ContactDetailScreen(target: DetailTarget, onClose: () -> Unit, onCall: (String) -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val groupedBg = if (dark) Color.Black else Color(0xFFF2F2F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White

    var number by remember { mutableStateOf(target.number) }
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(target.startEditing) }
    var loaded by remember { mutableStateOf<Loaded?>(null) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(number, refresh) {
        val result = withContext(Dispatchers.IO) {
            val d = loadContactDetail(context, number)
            val p = if (number.isBlank()) null else loadContactPhoto(context, number, true, 700)
            d to p
        }
        loaded = Loaded(result.first)
        photo = result.second
    }

    BackHandler {
        if (editing && !target.startEditing) editing = false else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(groupedBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        val l = loaded
        if (l != null) {
            if (editing) {
                EditMode(
                    initial = l.detail,
                    number = number,
                    currentPhoto = photo,
                    card = card,
                    onCancel = { if (target.startEditing) onClose() else editing = false },
                    onSaved = { first ->
                        if (first.isNotBlank()) number = first
                        refresh++
                        editing = false
                        if (first.isBlank() && number.isBlank()) onClose()
                    },
                    onDeleted = onClose
                )
            } else {
                ViewMode(
                    number = number,
                    detail = l.detail,
                    photo = photo,
                    card = card,
                    onBack = onClose,
                    onEdit = { editing = true },
                    onCall = onCall
                )
            }
        }
    }
}

@Composable
private fun BigAvatar(photo: ImageBitmap?, name: String?, size: Dp) {
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        InitialsAvatar(name, size, (size.value * 0.4f).sp)
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    card: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(card)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = IOS.Blue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = IOS.Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ViewMode(
    number: String,
    detail: ContactDetail?,
    photo: ImageBitmap?,
    card: Color,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    val name = detail?.name?.takeIf { it.isNotBlank() }
        ?: formatNumber(number).ifBlank { "Bilinmeyen" }
    val phones = when {
        detail != null && detail.phones.isNotEmpty() -> detail.phones
        number.isNotBlank() -> listOf(PhoneEntry(0, number, "telefon"))
        else -> emptyList()
    }
    val first = phones.firstOrNull()?.number
    val dividerColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.ArrowBackIosNew, null,
                    tint = IOS.Blue, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Geri", color = IOS.Blue, fontSize = 17.sp)
            }
            Spacer(Modifier.weight(1f))
            if (detail != null) {
                Text(
                    "Düzenle", color = IOS.Blue, fontSize = 17.sp,
                    modifier = Modifier.clickable(onClick = onEdit)
                )
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BigAvatar(photo, detail?.name, 112.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                name,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(Icons.Filled.ChatBubble, "Mesaj", card, Modifier.weight(1f)) {
                if (first != null) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$first")))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Mesaj uygulaması açılamadı", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ActionButton(Icons.Filled.Call, "Ara", card, Modifier.weight(1f)) {
                if (first != null) onCall(first)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (phones.isNotEmpty()) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(card)
            ) {
                phones.forEachIndexed { i, p ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onCall(p.number) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(p.label, fontSize = 13.sp, color = IOS.Gray)
                        Text(formatNumber(p.number), fontSize = 17.sp, color = IOS.Blue)
                    }
                    if (i < phones.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = dividerColor
                        )
                    }
                }
            }
        }

        if (detail == null && number.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Yeni Kişi Oluştur",
                color = IOS.Blue,
                fontSize = 17.sp,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(card)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FieldRow(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
        ),
        cursorBrush = SolidColor(IOS.Blue),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = IOS.Gray, fontSize = 17.sp)
                inner()
            }
        }
    )
}

@Composable
private fun EditMode(
    initial: ContactDetail?,
    number: String,
    currentPhoto: ImageBitmap?,
    card: Color,
    onCancel: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dividerColor = MaterialTheme.colorScheme.surfaceVariant

    var name by remember { mutableStateOf(initial?.name ?: "") }
    val phones = remember {
        mutableStateListOf<PhoneEntry>().apply {
            when {
                initial != null && initial.phones.isNotEmpty() -> addAll(initial.phones)
                number.isNotBlank() -> add(PhoneEntry(0, number, "cep"))
                else -> add(PhoneEntry(0, "", "cep"))
            }
        }
    }
    var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = decodeSquareBitmap(context, uri)
            if (bmp != null) {
                pendingPhoto = bmp
                removePhoto = false
            } else {
                Toast.makeText(context, "Fotoğraf açılamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (ok) pending?.invoke()
        else Toast.makeText(context, "Rehbere yazma izni gerekli", Toast.LENGTH_SHORT).show()
        pending = null
    }

    fun withWrite(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) action() else {
            pending = action
            permLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    fun save() = withWrite {
        val entries = phones.toList()
        val trimmed = name.trim()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                saveContact(context, initial, trimmed, entries, pendingPhoto, removePhoto)
            }
            if (ok) {
                onSaved(entries.firstOrNull { it.number.isNotBlank() }?.number?.trim() ?: "")
            } else {
                Toast.makeText(context, "Kaydedilemedi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val canSave = name.isNotBlank() || phones.any { it.number.isNotBlank() }
    val shownPhoto: ImageBitmap? = when {
        pendingPhoto != null -> pendingPhoto!!.asImageBitmap()
        removePhoto -> null
        else -> currentPhoto
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("İptal", color = IOS.Blue, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onCancel))
            Spacer(Modifier.weight(1f))
            Text(
                "Bitti",
                color = IOS.Blue,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .alpha(if (canSave) 1f else 0.4f)
                    .clickable(enabled = canSave) { save() }
            )
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BigAvatar(shownPhoto, name.ifBlank { initial?.name }, 112.dp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    if (shownPhoto != null) "Fotoğrafı Düzenle" else "Fotoğraf Ekle",
                    color = IOS.Blue,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { picker.launch("image/*") }
                )
                if (shownPhoto != null) {
                    Text(
                        "Kaldır",
                        color = IOS.Red,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable {
                            pendingPhoto = null
                            removePhoto = true
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(card)
        ) {
            FieldRow(
                value = name,
                onChange = { name = it },
                placeholder = "Ad Soyad",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(card)
        ) {
            phones.forEachIndexed { i, p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.RemoveCircle,
                        contentDescription = "Numarayı sil",
                        tint = IOS.Red,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { phones.removeAt(i) }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(p.label, color = IOS.Blue, fontSize = 15.sp, modifier = Modifier.width(52.dp))
                    FieldRow(
                        value = p.number,
                        onChange = { phones[i] = p.copy(number = it) },
                        placeholder = "Telefon",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Phone
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 46.dp),
                    thickness = 0.5.dp,
                    color = dividerColor
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { phones.add(PhoneEntry(0, "", "cep")) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AddCircle, null, tint = IOS.Green, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("telefon ekle", fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        if (initial != null) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Kişiyi Sil",
                color = IOS.Red,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(card)
                    .clickable { showDelete = true }
                    .padding(vertical = 13.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Kişiyi Sil") },
            text = { Text("Bu kişi rehberden silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    withWrite {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { deleteContact(context, initial.contactId) }
                            if (ok) onDeleted()
                            else Toast.makeText(context, "Silinemedi", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Sil", color = IOS.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Vazgeç") }
            }
        )
    }
}
