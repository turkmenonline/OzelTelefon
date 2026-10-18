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
import android.provider.CallLog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.ozel.telefon.HistoryEntry
import com.ozel.telefon.LinkedAccount
import com.ozel.telefon.blockNumber
import com.ozel.telefon.formatTr
import com.ozel.telefon.loadCallHistory
import com.ozel.telefon.loadLinkedAccounts
import com.ozel.telefon.numbersMatch
import com.ozel.telefon.peekContact
import com.ozel.telefon.peekPhoto
import com.ozel.telefon.sendSms
import com.ozel.telefon.setStarred
import com.ozel.telefon.shareContact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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

/** Kart açılmadan önce arka planda hazırlanan veri: kart kayarken içerik sonradan zıplamasın. */
class DetailBundle internal constructor(
    internal val detail: ContactDetail?,
    internal val history: List<HistoryEntry>,
    internal val accounts: List<LinkedAccount>,
    val photo: ImageBitmap?
)

suspend fun loadDetailBundle(context: android.content.Context, number: String): DetailBundle =
    withContext(Dispatchers.IO) {
        val d = loadContactDetail(context, number)
        val p = if (number.isBlank()) null else loadContactPhoto(context, number, true, 700)
        val nums = (d?.phones?.map { it.number } ?: emptyList()) + number
        val h = loadCallHistory(context, nums)
        val a = if (d != null) loadLinkedAccounts(context, d.contactId, d.name) else emptyList()
        DetailBundle(d, h, a, p)
    }

private class Loaded(
    val detail: ContactDetail?,
    val history: List<HistoryEntry>,
    val accounts: List<LinkedAccount>
)

/** iOS tarzı kişi kartı + düzenleme ekranı. */
@Composable
fun ContactDetailScreen(
    target: DetailTarget,
    tabBarHeight: Dp,
    initial: DetailBundle?,
    onClose: () -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val groupedBg = if (dark) Color.Black else Color(0xFFF2F2F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White

    var number by remember { mutableStateOf(target.number) }
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(target.startEditing) }
    var loaded by remember {
        mutableStateOf(initial?.let { Loaded(it.detail, it.history, it.accounts) })
    }
    // Önbellekteki küçük foto anında gösterilir; yüksek çözünürlüklüsü yüklenince değişir.
    var photo by remember { mutableStateOf(initial?.photo ?: peekPhoto(target.number, 200)) }

    LaunchedEffect(number, refresh) {
        // Önceden hazırlanmış veri varsa ilk açılışta tekrar yükleme.
        if (initial != null && refresh == 0 && number == target.number) return@LaunchedEffect
        val b = loadDetailBundle(context, number)
        loaded = Loaded(b.detail, b.history, b.accounts)
        photo = b.photo
    }

    BackHandler {
        if (editing && !target.startEditing) editing = false else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            // Görüntüleme modunda tab bar altta görünür kalır; düzenlemede tam ekran olur.
            .padding(bottom = if (editing) 0.dp else tabBarHeight)
            .background(groupedBg)
            // boş alana dokunuşlar alttaki listeye geçmesin
            .pointerInput(Unit) { detectTapGestures { } }
            .then(
                if (editing) Modifier.statusBarsPadding().navigationBarsPadding().imePadding()
                else Modifier
            )
    ) {
        val l = loaded
        if (editing) {
            if (l != null) {
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
            }
        } else {
            ViewMode(
                number = number,
                detail = l?.detail,
                history = l?.history ?: emptyList(),
                accounts = l?.accounts ?: emptyList(),
                loading = l == null,
                photo = photo,
                card = card,
                onBack = onClose,
                onEdit = { editing = true },
                onCall = onCall
            )
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

// ───────────────────────── Görüntüleme modu (iOS 26 tarzı kişi kartı) ─────────────────────────

private val TRD = Locale("tr", "TR")
private val histTime by lazy { SimpleDateFormat("HH:mm", TRD) }
private val histDay by lazy { SimpleDateFormat("d MMMM yyyy", TRD) }

private fun dayLabel(date: Long, today: Long): String {
    val days = ((today - midnight(date)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Bugün"
        days == 1 -> "Dün"
        else -> histDay.format(Date(date))
    }
}

private fun typeLabel(e: HistoryEntry): String = when (e.type) {
    CallLog.Calls.INCOMING_TYPE -> "Gelen Arama"
    CallLog.Calls.OUTGOING_TYPE -> if (e.duration > 0) "Giden Arama" else "Aramadan Vazgeçildi"
    CallLog.Calls.MISSED_TYPE -> "Cevapsız Arama"
    CallLog.Calls.REJECTED_TYPE -> "Reddedilen Arama"
    CallLog.Calls.BLOCKED_TYPE -> "Engellenen Arama"
    else -> "Arama"
}

private fun durationText(e: HistoryEntry): String? {
    val talked = e.type == CallLog.Calls.INCOMING_TYPE || e.type == CallLog.Calls.OUTGOING_TYPE
    if (!talked || e.duration <= 0) return null
    val m = e.duration / 60
    val sec = e.duration % 60
    return when {
        m == 0L -> "$sec saniye"
        sec == 0L -> "$m dakika"
        else -> "$m dk. $sec sn."
    }
}

/** Fotoğrafın ortalama rengi: kayınca sabitlenen üst çubuğun rengi. */
private fun averageColor(img: ImageBitmap?): Color {
    if (img == null) return Color(0xFF8A8D96)
    return try {
        val one = Bitmap.createScaledBitmap(img.asAndroidBitmap(), 1, 1, true)
        Color(one.getPixel(0, 0)).copy(alpha = 1f)
    } catch (e: Exception) {
        Color(0xFF8A8D96)
    }
}

@Composable
private fun GlassAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF8E8E93).copy(alpha = 0.38f))
            .iosClickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val c = Color.White.copy(alpha = if (enabled) 1f else 0.45f)
        Icon(icon, contentDescription = label, tint = c, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = c, fontSize = 12.sp)
    }
}

@Composable
private fun ActionRow(
    canCall: Boolean,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    onVideo: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassAction(F7.BubbleLeftFill, "mesaj", canCall, Modifier.weight(1f), onMessage)
        GlassAction(F7.PhoneFill, "ara", canCall, Modifier.weight(1f), onCall)
        GlassAction(F7.VideocamFill, "görüntülü", true, Modifier.weight(1f), onVideo)
        GlassAction(F7.EnvelopeFill, "e-posta", false, Modifier.weight(1f)) { }
    }
}

@Composable
private fun GlassTopButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF8E8E93).copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun CardColumn(card: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(card)
    ) { content() }
}

@Composable
private fun TextRow(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .iosClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun ViewMode(
    number: String,
    detail: ContactDetail?,
    history: List<HistoryEntry>,
    accounts: List<LinkedAccount>,
    loading: Boolean,
    photo: ImageBitmap?,
    card: Color,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val onBg = MaterialTheme.colorScheme.onBackground
    val dividerColor = MaterialTheme.colorScheme.surfaceVariant

    val name = detail?.name?.takeIf { it.isNotBlank() }
        ?: peekContact(number)?.name
        ?: formatNumber(number).ifBlank { "Bilinmeyen" }
    val phones = when {
        detail != null && detail.phones.isNotEmpty() ->
            detail.phones.distinctBy { it.number.filter { ch -> ch.isDigit() }.takeLast(10) }
        number.isNotBlank() -> listOf(PhoneEntry(0, number, "telefon"))
        else -> emptyList()
    }
    val first = phones.firstOrNull()?.number
    val lastUsed = history.firstOrNull()?.number
    val lastUsedIndex = if (lastUsed == null) -1 else phones.indexOfFirst { numbersMatch(lastUsed, it.number) }
    val today = remember { midnight(System.currentTimeMillis()) }

    fun message() { if (first != null) sendSms(context, first) }
    fun call() { if (first != null) onCall(first) }
    fun video() {
        Toast.makeText(context, "Görüntülü arama bu uygulamada yok", Toast.LENGTH_SHORT).show()
    }

    // Hızlı Aramaya ekle (rehbere yazma izni gerekir)
    fun doStar() {
        val id = detail?.contactId
        if (id == null) {
            Toast.makeText(context, "Önce kişiyi kaydedin", Toast.LENGTH_SHORT).show()
        } else {
            val ok = setStarred(context, id, true)
            Toast.makeText(context, if (ok) "Hızlı Aramaya eklendi" else "Eklenemedi", Toast.LENGTH_SHORT).show()
        }
    }
    val starPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) doStar() else Toast.makeText(context, "Rehbere yazma izni gerekli", Toast.LENGTH_SHORT).show()
    }
    fun star() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) doStar() else starPerm.launch(Manifest.permission.WRITE_CONTACTS)
    }

    // Kayma + sabitlenen üst çubuk
    val scroll = rememberScrollState()
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val headerH = screenW * 1.38f
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pinnedH = statusTop + 152.dp
    val startPx = with(density) { (headerH - pinnedH - 34.dp).toPx() }
    val fadePx = with(density) { 34.dp.toPx() }
    val nameFadeStart = with(density) { (headerH - pinnedH - 200.dp).toPx() }
    val nameFadeLen = with(density) { 70.dp.toPx() }
    val pinnedAlpha by remember(startPx, fadePx) {
        derivedStateOf { ((scroll.value - startPx) / fadePx).coerceIn(0f, 1f) }
    }
    val showPinned by remember(startPx) { derivedStateOf { scroll.value > startPx } }
    val tint by produceState(Color(0xFF8A8D96), photo) {
        value = withContext(Dispatchers.Default) { averageColor(photo) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
        ) {
            // ── Fotoğraflı başlık ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerH)
            ) {
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF9EA2AD), Color(0xFF6F737D)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            initialsOf(name) ?: "",
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Light,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        name,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .graphicsLayer {
                                alpha = 1f - ((scroll.value - nameFadeStart) / nameFadeLen).coerceIn(0f, 1f)
                            }
                    )
                    Spacer(Modifier.height(14.dp))
                    ActionRow(first != null, { message() }, { call() }, { video() })
                }
            }

            // ── Kartlar ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(0.dp))

                if (history.isNotEmpty()) {
                    val groups = remember(history, today) { history.groupBy { dayLabel(it.date, today) }.toList() }
                    CardColumn(card) {
                        Spacer(Modifier.height(4.dp))
                        groups.forEach { (label, entries) ->
                            Text(
                                label,
                                fontSize = 17.sp,
                                color = onBg,
                                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 8.dp)
                            )
                            entries.forEach { e ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                                ) {
                                    Text(
                                        histTime.format(Date(e.date)),
                                        fontSize = 13.sp,
                                        color = onBg.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .width(44.dp)
                                            .padding(top = 1.dp)
                                    )
                                    Column {
                                        Text(
                                            typeLabel(e),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (e.type == CallLog.Calls.MISSED_TYPE) IOS.Red else onBg
                                        )
                                        durationText(e)?.let { Text(it, fontSize = 13.sp, color = IOS.Gray) }
                                    }
                                }
                            }
                        }
                    }
                }

                // Kişi Fotoğrafı ve Poster
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(card)
                        .iosClickable(onClick = onEdit)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BigAvatar(photo, name, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Kişi Fotoğrafı ve Poster", fontSize = 17.sp, color = onBg)
                }

                // Numaralar
                if (phones.isNotEmpty()) {
                    CardColumn(card) {
                        phones.forEachIndexed { i, p ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .iosClickable { onCall(p.number) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.label, fontSize = 14.sp, color = onBg)
                                    if (i == lastUsedIndex) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "SON KULLANILAN",
                                            fontSize = 8.sp,
                                            color = IOS.Gray,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(dividerColor)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(formatTr(p.number), fontSize = 17.sp, color = IOS.Blue)
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

                // Notlar
                CardColumn(card) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 92.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onEdit
                            )
                    ) {
                        Text("Notlar", fontSize = 14.sp, color = onBg, modifier = Modifier.padding(16.dp, 10.dp))
                    }
                }

                if (!loading && detail == null && number.isNotBlank()) {
                    CardColumn(card) { TextRow("Yeni Kişi Oluştur", IOS.Blue, onEdit) }
                }

                // Mesaj / Paylaş / Hızlı Arama
                if (first != null) {
                    CardColumn(card) {
                        TextRow("Mesaj Gönder", IOS.Blue) { message() }
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = dividerColor)
                        TextRow("Kişiyi Paylaş", IOS.Blue) { shareContact(context, name, first) }
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = dividerColor)
                        TextRow("Hızlı Aramaya Ekle", IOS.Blue) { star() }
                    }
                    CardColumn(card) {
                        TextRow("Acil Durum Kişileri'ne Ekle", IOS.Blue) {
                            Toast.makeText(
                                context,
                                "Acil durum kişileri telefonun güvenlik ayarlarından eklenir",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    CardColumn(card) {
                        TextRow("Arayanı Engelle", IOS.Red) {
                            val ok = blockNumber(context, first)
                            Toast.makeText(
                                context,
                                if (ok) "Numara engellendi" else "Engellenemedi (varsayılan telefon uygulaması olmalı)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                // Bağlı kişiler
                if (accounts.isNotEmpty()) {
                    Column {
                        Text(
                            "BAĞLI KİŞİLER",
                            fontSize = 13.sp,
                            color = IOS.Gray,
                            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                        )
                        CardColumn(card) {
                            accounts.forEachIndexed { i, a ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(a.label, fontSize = 15.sp, color = onBg)
                                        Text(a.name, fontSize = 15.sp, color = IOS.Blue)
                                    }
                                    Icon(F7.ChevronRight, null, tint = IOS.Gray, modifier = Modifier.size(20.dp))
                                }
                                if (i < accounts.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        thickness = 0.5.dp,
                                        color = dividerColor
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // ── Kayınca sabitlenen küçük başlık (fotoğrafın ortalama renginde) ──
        if (showPinned) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = pinnedAlpha }
                    .background(tint.copy(alpha = 0.97f))
                    .statusBarsPadding()
                    .padding(top = 6.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BigAvatar(photo, name, 40.dp)
                Spacer(Modifier.height(2.dp))
                Text(name, fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                ActionRow(first != null, { message() }, { call() }, { video() })
            }
        }

        // ── Üstte hep duran Geri / Düzenle ──
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassTopButton(Modifier.size(36.dp), onBack) {
                Icon(F7.ChevronLeft, "Geri", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            if (detail != null) {
                GlassTopButton(Modifier, onEdit) {
                    Text(
                        "Düzenle",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }
        }
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
                        F7.MinusCircleFill,
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
                Icon(F7.PlusCircleFill, null, tint = IOS.Green, modifier = Modifier.size(24.dp))
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
