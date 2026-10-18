package com.ozel.telefon

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import android.widget.Toast

/** Kişi kartındaki arama geçmişi satırı. */
data class HistoryEntry(val date: Long, val type: Int, val duration: Long, val number: String)

/** "Bağlı kişiler" satırı: hesap adı (Gmail, iCloud...) ve kişi adı. */
data class LinkedAccount(val label: String, val name: String)

private fun tail(n: String): String = n.filter { it.isDigit() }.takeLast(10)

/** İki numara biçimi farklı olsa da aynı kişiye mi ait? (son 10 hane) */
fun numbersMatch(a: String, b: String): Boolean = tail(a).isNotEmpty() && tail(a) == tail(b)

/** Verilen numaralara ait son aramalar (en yeni başta). */
fun loadCallHistory(context: Context, numbers: List<String>, limit: Int = 12): List<HistoryEntry> {
    val keys = numbers.map { tail(it) }.filter { it.isNotEmpty() }.toSet()
    if (keys.isEmpty()) return emptyList()
    val out = mutableListOf<HistoryEntry>()
    try {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            var scanned = 0
            while (c.moveToNext() && out.size < limit && scanned < 2000) {
                scanned++
                val n = c.getString(0) ?: continue
                if (tail(n) in keys) {
                    out.add(HistoryEntry(c.getLong(2), c.getInt(1), c.getLong(3), n))
                }
            }
        }
    } catch (e: Exception) {
        // izin yok
    }
    return out
}

private fun accountLabel(type: String?): String = when {
    type == null -> "Telefon"
    type == "com.google" -> "Gmail"
    type.contains("apple", ignoreCase = true) -> "iCloud"
    type.contains("samsung", ignoreCase = true) || type.contains("osp") -> "Samsung"
    type.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
    type.contains("sim", ignoreCase = true) -> "SIM"
    type.contains("phone", ignoreCase = true) || type.contains("local", ignoreCase = true) -> "Telefon"
    else -> type.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

/** Kişinin hangi hesaplarda kayıtlı olduğunu döndürür. */
fun loadLinkedAccounts(context: Context, contactId: Long, name: String): List<LinkedAccount> {
    if (contactId < 0) return emptyList()
    val out = mutableListOf<LinkedAccount>()
    try {
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val label = accountLabel(c.getString(0))
                if (out.none { it.label == label }) out.add(LinkedAccount(label, name))
            }
        }
    } catch (e: Exception) {
        // izin yok
    }
    return out
}

/** Kişiyi yıldızlar / yıldızı kaldırır (Hızlı Arama). WRITE_CONTACTS gerekir. */
fun setStarred(context: Context, contactId: Long, starred: Boolean): Boolean {
    return try {
        val values = ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
        }
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        context.contentResolver.update(uri, values, null, null) > 0
    } catch (e: Exception) {
        false
    }
}

/** Numarayı engeller. Sadece varsayılan telefon uygulaması yapabilir. */
fun blockNumber(context: Context, number: String): Boolean {
    return try {
        val values = ContentValues().apply {
            put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        }
        context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
    } catch (e: Exception) {
        false
    }
}

/** Arama kaydından verilen kayıtları siler. WRITE_CALL_LOG gerekir. */
fun deleteCallLogs(context: Context, ids: List<Long>): Boolean {
    return try {
        ids.chunked(400).forEach { chunk ->
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN (${chunk.joinToString(",")})",
                null
            )
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun sendSms(context: Context, number: String) {
    if (number.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
    } catch (e: Exception) {
        Toast.makeText(context, "Mesaj uygulaması açılamadı", Toast.LENGTH_SHORT).show()
    }
}

fun shareContact(context: Context, name: String, number: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$name\n${formatNumber(number)}")
        }
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: Exception) {
        Toast.makeText(context, "Paylaşılamadı", Toast.LENGTH_SHORT).show()
    }
}

/** Türkiye numaralarını iPhone gibi "0 (551) 002 95 09" biçiminde gösterir; diğerleri olduğu gibi. */
fun formatTr(number: String): String {
    val d = number.filter { it.isDigit() }
    val core = when {
        d.length == 12 && d.startsWith("90") -> d.substring(2)
        d.length == 11 && d.startsWith("0") -> d.substring(1)
        d.length == 10 -> d
        else -> return formatNumber(number)
    }
    if (!core.startsWith("5") && core.length != 10) return formatNumber(number)
    return "0 (${core.substring(0, 3)}) ${core.substring(3, 6)} ${core.substring(6, 8)} ${core.substring(8, 10)}"
}

/** Engellenen numaraların (son 9 hane) kümesi. Varsayılan telefon uygulaması değilse boş döner. */
fun loadBlockedKeys(context: Context): Set<String> {
    return try {
        val out = HashSet<String>()
        context.contentResolver.query(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) out.add(blockKey(c.getString(0)))
        }
        out
    } catch (e: Exception) {
        emptySet()
    }
}

fun blockKey(number: String?): String =
    (number ?: "").filter { it.isDigit() }.takeLast(9)
