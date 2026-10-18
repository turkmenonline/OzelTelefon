package com.ozel.telefon.ui

import android.content.Context
import android.provider.CallLog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.outlined.Info
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
    val number: String,
    val cachedName: String?,
    val type: Int,
    val date: Long,
    val cachedLabel: String?
) {
    val missed: Boolean
        get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
}

data class RecentGroup(val first: RecentCall, val count: Int, val key: String)

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
                CallLog.Calls.CACHED_NUMBER_LABEL
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && list.size < 300) {
                val numType = if (c.isNull(4)) -1 else c.getInt(4)
                list.add(
                    RecentCall(
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
            out[out.lastIndex] = last.copy(count = last.count + 1)
        } else {
            var k = "${c.date}|${c.number}"
            while (!seen.add(k)) k += "_"
            out.add(RecentGroup(c, 1, k))
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

@Composable
fun RecentsScreen(
    hasPermission: Boolean,
    listState: LazyListState,
    onCall: (String) -> Unit,
    onInfo: (String) -> Unit
) {
    var filter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    // Veri ListData'da tutulur; sekme değişince diskten yeniden okunmaz.
    val items = ListData.recents
    val today = ListData.today

    val groups = remember(items, filter, query) {
        val base = if (filter == 1) items.filter { it.missed } else items
        val searched = if (query.isBlank()) base else base.filter {
            (it.cachedName?.contains(query, ignoreCase = true) == true) || it.number.contains(query)
        }
        group(searched)
    }

    val dividerColor = MaterialTheme.colorScheme.surfaceVariant

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header", contentType = "header") {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Segmented(listOf("Tümü", "Cevapsız"), filter) { filter = it }
                }
                LargeTitle("Son Aramalar")
            }
        }
        item(key = "search", contentType = "search") {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Arayın",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
        } else if (groups.isEmpty()) {
            item(key = "empty") { Text("Arama yok.", color = IOS.Gray, modifier = Modifier.padding(20.dp)) }
        }
        items(groups, key = { it.key }, contentType = { "recent" }) { g ->
            RecentRow(g, today, dividerColor, onCall, onInfo)
        }
    }
}

@Composable
private fun RecentRow(
    group: RecentGroup,
    today: Long,
    dividerColor: Color,
    onCall: (String) -> Unit,
    onInfo: (String) -> Unit
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
    val title = remember(contactName, number, group.count) {
        (contactName ?: formatNumber(number).ifBlank { "Bilinmeyen" }) +
            if (group.count > 1) " (${group.count})" else ""
    }
    val subtitle = when {
        contactName == null -> "Bilinmeyen"
        else -> call.cachedLabel ?: info?.label ?: "telefon"
    }
    val outgoing = call.type == CallLog.Calls.OUTGOING_TYPE
    val dateText = remember(call.date, today) { formatDate(call.date, today) }

    Column(Modifier.clickable { onCall(call.number) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            ContactAvatar(call.number, contactName, 42.dp, 18.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (call.missed) IOS.Red else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (outgoing) {
                        Icon(
                            Icons.Filled.CallMade,
                            contentDescription = "Giden arama",
                            tint = IOS.Gray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(subtitle, fontSize = 13.sp, color = IOS.Gray, maxLines = 1)
                }
            }
            Text(dateText, fontSize = 14.sp, color = IOS.Gray)
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Outlined.Info,
                contentDescription = "Bilgi",
                tint = IOS.Blue,
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onInfo(call.number) }
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 70.dp),
            thickness = 0.5.dp,
            color = dividerColor
        )
    }
}
