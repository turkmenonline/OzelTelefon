package com.ozel.telefon

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import java.io.ByteArrayOutputStream

data class PhoneEntry(val dataId: Long, val number: String, val label: String)

data class ContactDetail(
    val contactId: Long,
    val rawContactId: Long,
    val name: String,
    val phones: List<PhoneEntry>
)

/** Numaradan rehber kaydını (isim + tüm numaralar) yükler. Kayıt yoksa null. */
fun loadContactDetail(context: Context, number: String): ContactDetail? {
    if (number.isBlank()) return null
    return try {
        val lookup = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        var contactId = -1L
        var name = ""
        context.contentResolver.query(
            lookup,
            arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                contactId = c.getLong(0)
                name = c.getString(1) ?: ""
            }
        }
        if (contactId < 0) return null

        var raw = -1L
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            null
        )?.use { if (it.moveToFirst()) raw = it.getLong(0) }

        val phones = mutableListOf<PhoneEntry>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone._ID, Phone.NUMBER, Phone.TYPE, Phone.LABEL),
            "${Phone.CONTACT_ID}=?",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                phones.add(
                    PhoneEntry(
                        c.getLong(0),
                        c.getString(1) ?: "",
                        phoneTypeLabel(c.getInt(2), c.getString(3))
                    )
                )
            }
        }
        ContactDetail(contactId, raw, name, phones)
    } catch (e: Exception) {
        null
    }
}

/** Galeriden seçilen görseli ortadan kare kırpıp küçültür. */
fun decodeSquareBitmap(context: Context, uri: Uri, maxSize: Int = 720): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outWidth > 0) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val square = Bitmap.createBitmap(src, x, y, side, side)
        if (side > maxSize) Bitmap.createScaledBitmap(square, maxSize, maxSize, true) else square
    } catch (e: Exception) {
        null
    }
}

/**
 * Kişiyi kaydeder. original == null ise yeni kişi oluşturur.
 * phones: dataId > 0 olanlar güncellenir, 0 olanlar eklenir, listede olmayanlar silinir.
 */
fun saveContact(
    context: Context,
    original: ContactDetail?,
    name: String,
    phones: List<PhoneEntry>,
    photo: Bitmap?,
    removePhoto: Boolean
): Boolean {
    return try {
        val ops = ArrayList<ContentProviderOperation>()
        val photoBytes = photo?.let {
            val out = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        val isNew = original == null || original.rawContactId < 0

        if (isNew) {
            ops.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, null)
                    .withValue(RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, name)
                    .build()
            )
            phones.filter { it.number.isNotBlank() }.forEach { p ->
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, p.number.trim())
                        .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                        .build()
                )
            }
            if (photoBytes != null) {
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                        .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                        .withValue(Photo.PHOTO, photoBytes)
                        .build()
                )
            }
        } else {
            val orig = original!!
            val raw = orig.rawContactId
            // İsim: eski satırı sil, yenisini ekle
            ops.add(
                ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection(
                        "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
                        arrayOf(raw.toString(), StructuredName.CONTENT_ITEM_TYPE)
                    ).build()
            )
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, raw)
                    .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, name)
                    .build()
            )
            // Numaralar
            val keep = phones.filter { it.dataId > 0 }.map { it.dataId }.toSet()
            orig.phones.filter { it.dataId !in keep }.forEach { p ->
                ops.add(
                    ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection("${Data._ID}=?", arrayOf(p.dataId.toString()))
                        .build()
                )
            }
            phones.forEach { p ->
                if (p.dataId > 0) {
                    ops.add(
                        ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                            .withSelection("${Data._ID}=?", arrayOf(p.dataId.toString()))
                            .withValue(Phone.NUMBER, p.number.trim())
                            .build()
                    )
                } else if (p.number.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValue(Data.RAW_CONTACT_ID, raw)
                            .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                            .withValue(Phone.NUMBER, p.number.trim())
                            .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                            .build()
                    )
                }
            }
            // Fotoğraf
            if (removePhoto || photoBytes != null) {
                ops.add(
                    ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection(
                            "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
                            arrayOf(raw.toString(), Photo.CONTENT_ITEM_TYPE)
                        ).build()
                )
            }
            if (photoBytes != null) {
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, raw)
                        .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                        .withValue(Photo.PHOTO, photoBytes)
                        .build()
                )
            }
        }
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        clearContactCaches()
        true
    } catch (e: Exception) {
        false
    }
}

fun deleteContact(context: Context, contactId: Long): Boolean = try {
    val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
    val n = context.contentResolver.delete(uri, null, null)
    clearContactCaches()
    n > 0
} catch (e: Exception) {
    false
}
