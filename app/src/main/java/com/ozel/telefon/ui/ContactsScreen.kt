package com.ozel.telefon.ui

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozel.telefon.ui.theme.IOS
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

data class ContactItem(val name: String, val number: String)

private val TR = Locale("tr", "TR")

private val ALPHABET = listOf(
    "A", "B", "C", "Ç", "D", "E", "F", "G", "H", "I", "İ", "J", "K", "L", "M",
    "N", "O", "Ö", "P", "R", "S", "Ş", "T", "U", "Ü", "V", "Y", "Z", "#"
)

private fun sectionOf(name: String): String {
    val c = name.trim().firstOrNull() ?: return "#"
    val s = c.toString().uppercase(TR)
    return if (s in ALPHABET) s else "#"
}

internal fun loadContacts(context: Context): List<ContactItem> {
    val list = mutableListOf<ContactItem>()
    try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                list.add(ContactItem(name.trim(), number))
            }
        }
    } catch (e: Exception) {
        // izin yok
    }
    val collator = Collator.getInstance(TR)
    return list
        .distinctBy { it.name + it.number.filter { ch -> ch.isDigit() } }
        .sortedWith { a, b -> collator.compare(a.name, b.name) }
}

internal fun loadProfileName(context: Context): String? = try {
    context.contentResolver.query(
        ContactsContract.Profile.CONTENT_URI,
        arrayOf(ContactsContract.Profile.DISPLAY_NAME),
        null, null, null
    )?.use { if (it.moveToFirst()) it.getString(0) else null }
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    hasPermission: Boolean,
    listState: LazyListState,
    onCall: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    // Veri ListData'da tutulur; sekme değişince rehber yeniden okunup sıralanmaz.
    val all = ListData.contacts
    val profile = ListData.profile

    val grouped = remember(all, query) {
        val filtered = if (query.isBlank()) all else all.filter {
            it.name.contains(query, ignoreCase = true) || it.number.contains(query)
        }
        filtered.groupBy { sectionOf(it.name) }
            .entries
            .sortedBy { ALPHABET.indexOf(it.key) }
    }

    val showCard = profile != null && query.isBlank()
    val base = if (showCard) 3 else 2
    val starts = remember(grouped, base) {
        val map = LinkedHashMap<String, Int>()
        var idx = base
        for ((key, list) in grouped) {
            map[key] = idx
            idx += 1 + list.size
        }
        map
    }

    val scope = rememberCoroutineScope()
    val dividerColor = MaterialTheme.colorScheme.surfaceVariant

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "header", contentType = "header") {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Kişi ekle",
                            tint = IOS.Blue,
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { onAdd() }
                        )
                    }
                    LargeTitle("Kişiler")
                }
            }
            item(key = "search", contentType = "search") {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Ara",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            if (showCard) {
                item {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = dividerColor
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            InitialsAvatar(profile, 48.dp, 19.sp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    profile ?: "",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text("Kartım", fontSize = 14.sp, color = IOS.Gray)
                            }
                        }
                    }
                }
            }

            if (!hasPermission) {
                item {
                    Text(
                        "Kişileri görmek için rehber izni gerekli.",
                        color = IOS.Gray,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else if (grouped.isEmpty()) {
                item {
                    Text("Kişi bulunamadı.", color = IOS.Gray, modifier = Modifier.padding(20.dp))
                }
            }

            for ((letter, contacts) in grouped) {
                stickyHeader(key = "h_$letter", contentType = "header") {
                    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                        Text(
                            text = letter,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IOS.Gray,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = dividerColor
                        )
                    }
                }
                items(contacts, key = { "${it.name}|${it.number}" }, contentType = { "contact" }) { contact ->
                    Column(Modifier.clickable { onOpen(contact.number) }) {
                        ContactName(
                            contact.name,
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 36.dp, top = 13.dp, bottom = 13.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = dividerColor
                        )
                    }
                }
            }
        }

        if (grouped.isNotEmpty()) {
            AlphabetIndex(
                modifier = Modifier.align(Alignment.CenterEnd),
                onSelect = { letter ->
                    val from = ALPHABET.indexOf(letter)
                    val target = ALPHABET.drop(from).firstOrNull { it in starts }
                        ?: ALPHABET.reversed().firstOrNull { it in starts }
                    if (target != null) {
                        scope.launch { listState.scrollToItem(starts.getValue(target)) }
                    }
                }
            )
        }
    }
}

/** iOS gibi: soyadı (son kelime) kalın, öncesi normal. */
@Composable
private fun ContactName(name: String, modifier: Modifier) {
    val text = remember(name) {
        val trimmed = name.trim()
        val idx = trimmed.lastIndexOf(' ')
        buildAnnotatedString {
            if (idx > 0) {
                append(trimmed.substring(0, idx + 1))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(trimmed.substring(idx + 1)) }
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(trimmed) }
            }
        }
    }
    Text(
        text = text,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
private fun AlphabetIndex(modifier: Modifier, onSelect: (String) -> Unit) {
    var heightPx by remember { mutableIntStateOf(1) }
    val currentOnSelect by rememberUpdatedState(onSelect)

    fun pick(y: Float) {
        val i = (y / heightPx * ALPHABET.size).toInt().coerceIn(0, ALPHABET.size - 1)
        currentOnSelect(ALPHABET[i])
    }

    Column(
        modifier = modifier
            .width(22.dp)
            .fillMaxHeight(0.78f)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { pick(it.y) } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y) },
                    onVerticalDrag = { change, _ -> pick(change.position.y) }
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ALPHABET.forEach {
            Text(it, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = IOS.Blue)
        }
    }
}
