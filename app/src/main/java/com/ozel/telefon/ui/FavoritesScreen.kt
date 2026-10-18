package com.ozel.telefon.ui

import android.content.Context
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozel.telefon.phoneTypeLabel
import com.ozel.telefon.rememberResumeCount
import com.ozel.telefon.ui.theme.IOS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

internal data class Favorite(val name: String, val number: String, val label: String)

internal fun loadFavorites(context: Context): List<Favorite> {
    val list = mutableListOf<Favorite>()
    try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            ),
            "starred=1", null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                list.add(Favorite(name, number, phoneTypeLabel(c.getInt(2), c.getString(3))))
            }
        }
    } catch (e: Exception) {
        // izin yok
    }
    val collator = Collator.getInstance(Locale("tr", "TR"))
    return list.distinctBy { it.name }.sortedWith { a, b -> collator.compare(a.name, b.name) }
}

@Composable
fun FavoritesScreen(hasPermission: Boolean, onCall: (String) -> Unit) {
    // Veri ListData'da tutulur; sekme değişince rehber yeniden okunmaz.
    val items = ListData.favorites

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "header") { Column(Modifier.padding(top = 46.dp)) { LargeTitle("Hızlı Arama") } }
        if (items.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Rehberde yıldızladığın kişiler burada görünür.",
                    color = IOS.Gray,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
        items(items, key = { it.name }, contentType = { "fav" }) { fav ->
            Column(Modifier.clickable { onCall(fav.number) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    ContactAvatar(fav.number, fav.name, 42.dp, 17.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            fav.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(fav.label, fontSize = 13.sp, color = IOS.Gray)
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
