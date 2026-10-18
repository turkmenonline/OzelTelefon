package com.ozel.telefon.ui

import com.ozel.telefon.loadBlockedKeys
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ozel.telefon.loadContactPhoto
import com.ozel.telefon.lookupContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Son Aramalar, Kişiler ve Hızlı Arama verisi burada tutulur. Sekme değişince liste
 * yeniden okunmaz; veri değişince (arama kaydı/rehber) arka planda tazelenir.
 *
 * Tazeleme kendi kapsamında çalışır: ekran/composable iptal olsa da yarım kalmaz,
 * ve isteklerin hiçbiri kaybolmaz (biri sürerken gelen istek bitince tekrar çalışır).
 */
object ListData {
    var recents by mutableStateOf<List<RecentCall>>(emptyList())
        private set
    var contacts by mutableStateOf<List<ContactItem>>(emptyList())
        private set
    var profile by mutableStateOf<String?>(null)
        private set
    internal var favorites by mutableStateOf<List<Favorite>>(emptyList())
        private set
    var blocked by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Bugünün gece yarısı; satırlarda "Dün/gün adı" hesabı için. */
    var today by mutableLongStateOf(0L)
        private set

    /** Her tazelemede artar; avatar/isim önbelleği değiştiyse satırlar yeniden okusun diye. */
    var version by mutableIntStateOf(0)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = AtomicBoolean(false)
    private var needRecents = false
    private var needContacts = false

    /** Silinen aramaları listeden anında çıkarır (veritabanı silme arkadan gelir). */
    fun removeCalls(ids: Set<Long>) {
        recents = recents.filter { it.id !in ids }
    }

    /** Hemen döner; işi arka planda yapar. Ana iş parçacığından çağrılmalı. */
    fun refresh(context: Context, recents: Boolean = true, contacts: Boolean = true) {
        if (recents) needRecents = true
        if (contacts) needContacts = true
        if (!running.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            try {
                while (needRecents || needContacts) {
                    if (needRecents) {
                        needRecents = false
                        try { doRecents(app) } catch (e: Exception) { /* sonraki tazelemede dener */ }
                    }
                    if (needContacts) {
                        needContacts = false
                        try { doContacts(app) } catch (e: Exception) { /* sonraki tazelemede dener */ }
                    }
                }
            } finally {
                running.set(false)
            }
        }
    }

    private suspend fun doRecents(app: Context) {
        val calls = withContext(Dispatchers.IO) { loadRecents(app) }
        val numbers = calls.map { it.number }.distinct()
        // İlk ekrandaki kişileri önceden çöz, sonra listeyi göster.
        withContext(Dispatchers.IO) {
            numbers.take(25).forEach { lookupContact(app, it) }
            numbers.take(25).forEach { loadContactPhoto(app, it, false, 200) }
        }
        if (calls != recents) recents = calls
        today = midnight(System.currentTimeMillis())
        version++
        // Kalanlar arka planda hazırlanır.
        withContext(Dispatchers.IO) {
            numbers.drop(25).forEach { lookupContact(app, it) }
            numbers.drop(25).take(40).forEach { loadContactPhoto(app, it, false, 200) }
        }
    }

    private suspend fun doContacts(app: Context) {
        val (list, prof, favs) = withContext(Dispatchers.IO) {
            Triple(loadContacts(app), loadProfileName(app), loadFavorites(app))
        }
        val blk = withContext(Dispatchers.IO) { loadBlockedKeys(app) }
        if (blk != blocked) blocked = blk
        if (list != contacts) contacts = list
        if (prof != profile) profile = prof
        if (favs != favorites) favorites = favs
        version++
    }
}
