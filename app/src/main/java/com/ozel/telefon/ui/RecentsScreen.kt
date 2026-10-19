package com.ozel.telefon.ui

import androidx.compose.ui.graphics.TransformOrigin
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.CallLog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.ozel.telefon.deleteCallLogs
import kotlinx.coroutines.launch
import com.ozel.telefon.ContactInfo
import com.ozel.telefon.contactCached
import com.ozel.telefon.formatNumber
import com.ozel.telefon.lookupContact
import com.ozel.telefon.openContactCard
import com.ozel.telefon.peekContact
import com.ozel.telefon.phoneTypeLabel
import com.ozel.telefon.ui.theme.IOS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class RecentCall(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val type: Int,
    val date: Long,
    val cachedLabel: String?
) {
    val missed: Boolean
        get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
}

data class RecentGroup(val first: RecentCall, val count: Int, val key: String, val ids: List<Long>)

internal fun loadRecents(context: Context): List<RecentCall> {
    val list = mutableListOf<RecentCall>()
    try {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.CACHED_NUMBER_TYPE,
                CallLog.Calls.CACHED_NUMBER_LABEL,
                CallLog.Calls._ID
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && list.size < 300) {
                val numType = if (c.isNull(4)) -1 else c.getInt(4)
                list.add(
                    RecentCall(
                        id = c.getLong(6),
                        number = c.getString(0) ?: "",
                        cachedName = c.getString(1)?.takeIf { it.isNotBlank() },
                        type = c.getInt(2),
                        date = c.getLong(3),
                        cachedLabel = if (numType >= 0) phoneTypeLabel(numType, c.getString(5)) else null
                    )
                )
            }
        }
    } catch (e: Exception) {
        // izin yok
    }
    return list
}

/** Art arda gelen aynı kişiyle aynı türdeki aramaları "Ad (2)" gibi birleştirir. */
private fun group(calls: List<RecentCall>): List<RecentGroup> {
    val out = mutableListOf<RecentGroup>()
    val seen = HashSet<String>()
    for (c in calls) {
        val last = out.lastOrNull()
        if (last != null && last.first.number == c.number && last.first.missed == c.missed) {
            out[out.lastIndex] = last.copy(count = last.count + 1, ids = last.ids + c.id)
        } else {
            var k = "${c.date}|${c.number}"
            while (!seen.add(k)) k += "_"
            out.add(RecentGroup(c, 1, k, listOf(c.id)))
        }
    }
    return out
}

internal fun midnight(millis: Long): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

private val TRL = Locale("tr", "TR")

// SimpleDateFormat pahalı; bir kez kurulur. Sadece ana iş parçacığında (composition) kullanılır.
private val timeFmt by lazy { SimpleDateFormat("HH:mm", TRL) }
private val dayFmt by lazy { SimpleDateFormat("EEEE", TRL) }
private val dateFmt by lazy { SimpleDateFormat("dd.MM.yy", TRL) }

private fun formatDate(millis: Long, today: Long): String {
    val days = ((today - midnight(millis)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> timeFmt.format(Date(millis))
        days == 1 -> "Dün"
        days < 7 -> dayFmt.format(Date(millis))
        else -> dateFmt.format(Date(millis))
    }
}

/** Uzun basınca açılan menünün hedefi (satırın ekrandaki üst konumuyla). */
data class MenuTarget(val number: String, val name: String?, val rowTop: Float)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(
    hasPermission: Boolean,
    listState: LazyListState,
    onCall: (String) -> Unit,
    onInfo: (String) -> Unit,
    onMenu: (MenuTarget) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableIntStateOf(0) }
    val query = SearchMode.query
    var editing by remember { mutableStateOf(false) }
    var armedKey by remember { mutableStateOf<String?>(null) }
    var showEditMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    // Veri ListData'da tutulur; sekme değişince diskten yeniden okunmaz.
    val items = ListData.recents
    val today = ListData.today
    val favorites = ListData.favorites
    var showFilterMenu by remember { mutableStateOf(false) }

    val groups = remember(items, filter, query, ListData.version) {
        val base = when (filter) {
            1 -> items.filter { it.missed }
            2 -> emptyList()   // Sesli Mesaj: arama listesi yerine sesli mesaj satırı gösterilir
            else -> items
        }
        val searched = if (query.isBlank()) base else base.filter {
            (it.cachedName?.contains(query, ignoreCase = true) == true) || it.number.contains(query)
        }
        group(searched)
    }

    // --- Silme (WRITE_CALL_LOG gerekir) ---
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingAction?.invoke()
        else Toast.makeText(context, "Arama kaydını silmek için izin gerekli", Toast.LENGTH_SHORT).show()
        pendingAction = null
    }
    fun withLogWrite(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) action() else {
            pendingAction = action
            permLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
        }
    }
    fun deleteIds(ids: List<Long>) = withLogWrite {
        ListData.removeCalls(ids.toSet())            // liste anında güncellenir
        scope.launch {
            withContext(Dispatchers.IO) { deleteCallLogs(context, ids) }
            ListData.refresh(context, recents = true, contacts = false)
        }
    }

    // "Favorilere Ekle": rehberden kişi seç, yıldızla (STARRED), listeyi tazele.
    val pickFavorite = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val cid = context.contentResolver.query(
                        uri, arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID), null, null, null
                    )?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return@runCatching false
                    val cv = ContentValues().apply { put(ContactsContract.Contacts.STARRED, 1) }
                    context.contentResolver.update(
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cid), cv, null, null
                    ) > 0
                }.getOrDefault(false)
            }
            if (ok) ListData.refresh(context, recents = false, contacts = true)
            else Toast.makeText(context, "Favorilere eklenemedi", Toast.LENGTH_SHORT).show()
        }
    }

    val dividerColor = MaterialTheme.colorScheme.surfaceVariant

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarInset.current)) {
        item(key = "header", contentType = "header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    GlassPillButton(
                        text = if (editing) "Bitti" else "Düzenle",
                        onClick = {
                            if (editing) {
                                editing = false
                                armedKey = null
                            } else {
                                showEditMenu = true
                            }
                        }
                    )
                    if (showEditMenu) {
                        EditPopup(
                            onDismiss = { showEditMenu = false },
                            onNameAndPhoto = {
                                showEditMenu = false
                                Toast.makeText(context, "Bu özellik bu uygulamada yok", Toast.LENGTH_SHORT).show()
                            },
                            onAddFavorite = {
                                showEditMenu = false
                                pickFavorite.launch(
                                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                )
                            },
                            onSelect = {
                                showEditMenu = false
                                editing = true
                            }
                        )
                    }
                }
                Text(
                    "Aramalar",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Box(Modifier.align(Alignment.CenterEnd)) {
                    if (editing) {
                        GlassPillButton(
                            text = "Sil",
                            onClick = { if (groups.isNotEmpty()) confirmClear = true }
                        )
                    } else {
                        GlassCircleButton(onClick = { showFilterMenu = true }) {
                            FilterLinesIcon(MaterialTheme.colorScheme.onBackground)
                        }
                        if (showFilterMenu) {
                            FilterPopup(
                                selected = filter,
                                onSelect = {
                                    filter = it
                                    showFilterMenu = false
                                },
                                onDismiss = { showFilterMenu = false },
                                onClassicLayout = {
                                    showFilterMenu = false
                                    Toast.makeText(context, "Klasik düzen henüz yok", Toast.LENGTH_SHORT).show()
                                },
                                onManageFiltering = {
                                    showFilterMenu = false
                                    Toast.makeText(context, "Bu özellik bu uygulamada yok", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
        if (filter == 0 && favorites.isNotEmpty()) {
            item(key = "favorites", contentType = "favorites") {
                FavoritesGrid(favorites.take(8), onCall)
            }
        }
        item(key = "recents-title", contentType = "title") {
            Text(
                if (filter == 2) "Sesli Mesaj" else if (filter == 1) "Cevapsız Aramalar" else "Son Aramalar",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
            )
        }
        if (!hasPermission) {
            item(key = "noperm") {
                Text(
                    "Arama geçmişini görmek için izin gerekli.",
                    color = IOS.Gray,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else if (filter == 2) {
            item(key = "voicemail") { VoicemailBody() }
        } else if (groups.isEmpty()) {
            item(key = "empty") { Text("Arama yok.", color = IOS.Gray, modifier = Modifier.padding(20.dp)) }
        }
        items(groups, key = { it.key }, contentType = { "recent" }) { g ->
            RecentRow(
                group = g,
                today = today,
                dividerColor = dividerColor,
                editing = editing,
                armed = armedKey == g.key,
                onArm = { armedKey = if (armedKey == g.key) null else g.key },
                onDelete = {
                    armedKey = null
                    deleteIds(g.ids)
                },
                onCall = onCall,
                onInfo = onInfo,
                onMenu = onMenu
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(if (filter != 0 || query.isNotBlank()) "Listelenen aramalar silinsin mi?" else "Tüm aramalar silinsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    val ids = groups.flatMap { it.ids }
                    editing = false
                    armedKey = null
                    deleteIds(ids)
                }) { Text("Sil", color = IOS.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Vazgeç", color = IOS.Blue) }
            }
        )
    }
}

/** "Düzenle"ye basınca açılan iOS tarzı küçük menü. */
@Composable
private fun EditPopup(
    onDismiss: () -> Unit,
    onNameAndPhoto: () -> Unit,
    onAddFavorite: () -> Unit,
    onSelect: () -> Unit
) {
    // Seçim/kapatma önce kapanma animasyonunu oynatır, sonra asıl işlem çalışır.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dismissReq = { pending = onDismiss }
    val nameReq = { pending = onNameAndPhoto }
    val favReq = { pending = onAddFavorite }
    val selReq = { pending = onSelect }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dy = with(density) { 48.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, dy),
        onDismissRequest = dismissReq,
        properties = PopupProperties(focusable = true)
    ) {
      PopupAppear(TransformOrigin(0f, 0f), visible = pending == null, onGone = { pending?.invoke() }) {
        Column(
            Modifier
                .width(250.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            PopupRow("Ad ve Fotoğraf Ayarla", F7.PersonCropCircleFill, nameReq)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            PopupRow("Favorilere Ekle", F7.PhoneFill, favReq)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            PopupRow("Seç", F7.CheckmarkCircle, selReq)
        }
      }
    }
}

@Composable
private fun PopupRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .iosClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    group: RecentGroup,
    today: Long,
    dividerColor: Color,
    editing: Boolean,
    armed: Boolean,
    onArm: () -> Unit,
    onDelete: () -> Unit,
    onCall: (String) -> Unit,
    onInfo: (String) -> Unit,
    onMenu: (MenuTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val call = group.first
    val number = call.number
    val version = ListData.version

    // Önceden çözülmüşse ilk karede hazır; IO'ya hiç gitmez.
    val cached = remember(number, version) { peekContact(number) }
    val info by produceState<ContactInfo?>(cached, number, version) {
        if (value == null && !contactCached(number)) {
            value = withContext(Dispatchers.IO) { lookupContact(context, number) }
        }
    }
    val contactName = info?.name ?: call.cachedName
    // iOS 26: isim düz kalır, grup sayısı alt satırda tip etiketinin yanında ("cep (2)").
    val title = remember(contactName, number) {
        contactName ?: formatNumber(number).ifBlank { "Bilinmeyen" }
    }
    val subtitle = (when {
        contactName == null -> "Bilinmeyen"
        else -> call.cachedLabel ?: info?.label ?: "telefon"
    }) + if (group.count > 1) " (${group.count})" else ""
    val outgoing = call.type == CallLog.Calls.OUTGOING_TYPE
    val dateText = remember(call.date, today) { formatDate(call.date, today) }

    // Uzun basma menüsü için satırın ekrandaki konumu (state yazmaz, yeniden çizim tetiklemez).
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier
            .onGloballyPositioned { coords[0] = it }
            .iosClickable(
                enabled = !editing,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val y = coords[0]?.positionInRoot()?.y ?: 0f
                    onMenu(MenuTarget(number, contactName, y))
                }
            ) { onInfo(call.number) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp)
        ) {
            AnimatedVisibility(
                visible = editing,
                enter = expandHorizontally(tween(240)) + fadeIn(tween(240)),
                exit = shrinkHorizontally(tween(200)) + fadeOut(tween(160))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(IOS.Red)
                            .clickable(onClick = onArm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(F7.Minus, "Sil", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }
            ContactAvatar(call.number, contactName, 46.dp, 18.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (call.missed) IOS.Red else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // iOS: gelen/cevapsız ↙, giden ↗ (küçük gri ok)
                    CallDirectionIcon(outgoing = outgoing, tint = IOS.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text(subtitle, fontSize = 13.sp, color = IOS.Gray, maxLines = 1)
                }
            }
            if (armed) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(IOS.Red)
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) { Text("Sil", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            } else {
                Text(dateText, fontSize = 14.sp, color = IOS.Gray)
                AnimatedVisibility(
                    visible = !editing,
                    enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkHorizontally(tween(180)) + fadeOut(tween(120))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onCall(call.number) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                F7.PhoneFill,
                                contentDescription = "Ara",
                                tint = IOS.Blue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = if (editing) 108.dp else 74.dp),
            thickness = 0.5.dp,
            color = dividerColor
        )
    }
}

/** iOS 26 filtre menüsü: düzen seçici + liste filtresi + "Filtrelemeyi Yönetin" tek popup'ta. */
@Composable
private fun FilterPopup(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    onClassicLayout: () -> Unit,
    onManageFiltering: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dy = with(density) { 48.dp.roundToPx() }
    val labels = listOf("Aramalar", "Cevapsızlar", "Sesli Mesaj")
    val fg = MaterialTheme.colorScheme.onBackground
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dismissReq = { pending = onDismiss }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, dy),
        onDismissRequest = dismissReq,
        properties = PopupProperties(focusable = true)
    ) {
      PopupAppear(TransformOrigin(1f, 0f), visible = pending == null, onGone = { pending?.invoke() }) {
        Column(
            Modifier
                .width(290.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(glassSurfaceColor())
                .padding(vertical = 8.dp)
        ) {
            // Düzen seçici: Klasik / Birleştirilmiş (şimdilik yalnız Birleştirilmiş çalışır)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LayoutChoice("Klasik", false, { pending = onClassicLayout }, Modifier.weight(1f))
                LayoutChoice("Birleştirilmiş", true, dismissReq, Modifier.weight(1f))
            }
            HorizontalDivider(thickness = 0.5.dp, color = fg.copy(alpha = 0.12f))
            labels.forEachIndexed { i, label ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .iosClickable { pending = { onSelect(i) } }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(26.dp), contentAlignment = Alignment.CenterStart) {
                        if (i == selected) {
                            Icon(F7.CheckmarkCircle, null, tint = fg, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(label, fontSize = 17.sp, color = fg)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = fg.copy(alpha = 0.12f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .iosClickable { pending = onManageFiltering }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filtrelemeyi Yönetin", fontSize = 17.sp, color = fg)
            }
        }
      }
    }
}

@Composable
private fun LayoutChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fg = MaterialTheme.colorScheme.onBackground
    Column(
        modifier.iosClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(F7.PhoneFill, null, tint = if (selected) IOS.Blue else IOS.Gray, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) IOS.Blue else fg,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Favoriler: 4 sütunlu kutucuk ızgarası (iOS 26 Aramalar ekranının üstü). */
@Composable
private fun FavoritesGrid(favs: List<Favorite>, onCall: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        favs.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { f -> FavoriteTile(f, onCall, Modifier.weight(1f)) }
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FavoriteTile(fav: Favorite, onCall: (String) -> Unit, modifier: Modifier = Modifier) {
    val first = fav.name.substringBefore(' ')
    Column(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onCall(fav.number) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            ContactAvatar(fav.number, fav.name, 54.dp, 20.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Icon(F7.PhoneFill, null, tint = IOS.Gray, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
            Text(first, fontSize = 13.sp, color = IOS.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** iOS Son Aramalar'daki küçük yön oku: gelen ↙, giden ↗. */
@Composable
fun CallDirectionIcon(outgoing: Boolean, tint: Color, modifier: Modifier = Modifier.size(11.dp)) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = 1.7.dp.toPx()
        val m = w / 2f + 0.5f
        val s = size.width
        val cap = androidx.compose.ui.graphics.StrokeCap.Round
        val o = { x: Float, y: Float -> androidx.compose.ui.geometry.Offset(x, y) }
        if (outgoing) {
            // ↗: sol-alttan sağ-üste çizgi, sağ-üstte L biçimli başlık
            drawLine(tint, o(m, s - m), o(s - m, m), w, cap)
            drawLine(tint, o(s * 0.30f, m), o(s - m, m), w, cap)
            drawLine(tint, o(s - m, m), o(s - m, s * 0.70f), w, cap)
        } else {
            // ↙: sağ-üstten sol-alta çizgi, sol-altta L biçimli başlık
            drawLine(tint, o(s - m, m), o(m, s - m), w, cap)
            drawLine(tint, o(m, s * 0.30f), o(m, s - m), w, cap)
            drawLine(tint, o(m, s - m), o(s * 0.70f, s - m), w, cap)
        }
    }
}
