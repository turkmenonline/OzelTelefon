package com.ozel.telefon

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.util.LruCache
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

fun placeCall(context: Context, number: String) {
    if (number.isBlank()) return
    val telecom = context.getSystemService(TelecomManager::class.java)
    try {
        telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
    } catch (e: SecurityException) {
        Toast.makeText(context, "Arama izni gerekli", Toast.LENGTH_SHORT).show()
    }
}

fun callVoicemail(context: Context) {
    val telecom = context.getSystemService(TelecomManager::class.java)
    try {
        telecom.placeCall(Uri.fromParts("voicemail", "", null), Bundle())
    } catch (e: Exception) {
        Toast.makeText(context, "Sesli mesaj aranamadı", Toast.LENGTH_SHORT).show()
    }
}

fun formatNumber(number: String): String {
    if (number.isBlank()) return number
    return try {
        PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
    } catch (e: Exception) {
        number
    }
}

fun phoneTypeLabel(type: Int, custom: String?): String = when (type) {
    1 -> "ev"
    2 -> "cep"
    3 -> "iş"
    4 -> "iş faks"
    5 -> "ev faks"
    7 -> "diğer"
    0 -> custom?.takeIf { it.isNotBlank() } ?: "telefon"
    else -> "telefon"
}

data class ContactInfo(
    val name: String?,
    val photoUri: String?,
    val thumbUri: String?,
    val label: String?,
    val lookupUri: Uri?
)

private val infoCache = ConcurrentHashMap<String, ContactInfo>()
private val missCache = ConcurrentHashMap.newKeySet<String>()
private val photoCache = LruCache<String, ImageBitmap>(200)

/** Numaradan rehberdeki kişiyi (isim, foto, tür) bulur. İzin yoksa / bulunamazsa null. */
fun lookupContact(context: Context, number: String): ContactInfo? {
    if (number.isBlank()) return null
    infoCache[number]?.let { return it }
    if (missCache.contains(number)) return null
    val info = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.LOOKUP_KEY,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                ContactsContract.PhoneLookup.TYPE,
                ContactsContract.PhoneLookup.LABEL
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                ContactInfo(
                    name = c.getString(2),
                    photoUri = c.getString(3),
                    thumbUri = c.getString(4),
                    label = phoneTypeLabel(c.getInt(5), c.getString(6)),
                    lookupUri = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                )
            } else null
        }
    } catch (e: Exception) {
        null
    }
    if (info != null) infoCache[number] = info else missCache.add(number)
    return info
}

/** Kişi düzenlenince önbellekleri temizler. */
fun clearContactCaches() {
    infoCache.clear()
    missCache.clear()
    photoCache.evictAll()
}

/** Önbellekteki kişi bilgisi (disk/IPC yok); yoksa null. */
fun peekContact(number: String): ContactInfo? = infoCache[number]

/** Bu numara daha önce sorgulandı mı (bulundu ya da bulunamadı)? */
fun contactCached(number: String): Boolean =
    infoCache.containsKey(number) || missCache.contains(number)

/** Liste avatarı için önbellekteki fotoğraf (decode yok); yoksa null. */
fun peekPhoto(number: String, maxSize: Int): ImageBitmap? {
    val info = infoCache[number] ?: return null
    val uri = info.thumbUri ?: info.photoUri ?: return null
    return photoCache.get("$uri@$maxSize")
}

/** Bu numaranın listede gösterilecek fotoğrafı olabilir mi? Bilinen "yok" ise false. */
fun photoPossible(number: String): Boolean {
    if (missCache.contains(number)) return false
    val info = infoCache[number] ?: return true
    return info.thumbUri != null || info.photoUri != null
}

fun lookupName(context: Context, number: String): String? = lookupContact(context, number)?.name

private fun decodeBitmap(context: Context, uriString: String?, maxSize: Int): ImageBitmap? {
    if (uriString.isNullOrBlank()) return null
    val key = "$uriString@$maxSize"
    photoCache.get(key)?.let { return it }
    return try {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outWidth > 0) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }?.asImageBitmap()
        if (bmp != null) photoCache.put(key, bmp)
        bmp
    } catch (e: Exception) {
        null
    }
}

/** Kişi fotoğrafı: full=true büyük (çağrı ekranı), false küçük (liste). */
fun loadContactPhoto(context: Context, number: String, full: Boolean, maxSize: Int): ImageBitmap? {
    val info = lookupContact(context, number) ?: return null
    val uri = if (full) info.photoUri ?: info.thumbUri else info.thumbUri ?: info.photoUri
    return decodeBitmap(context, uri, maxSize)
}

/** (i) düğmesi: kişi varsa kartını, yoksa "kişi ekle" ekranını açar. */
fun openContactCard(context: Context, number: String) {
    try {
        val info = lookupContact(context, number)
        val intent = if (info?.lookupUri != null) {
            Intent(Intent.ACTION_VIEW, info.lookupUri)
        } else {
            Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                .putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Kişi kartı açılamadı", Toast.LENGTH_SHORT).show()
    }
}

/** Uygulama her öne geldiğinde artan bir sayaç; listeleri yenilemek için. */
@Composable
fun rememberResumeCount(): Int {
    val owner = LocalContext.current as? LifecycleOwner
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) count++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    return count
}
