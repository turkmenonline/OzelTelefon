package com.ozel.telefon.call

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import com.ozel.telefon.MainActivity
import com.ozel.telefon.R
import com.ozel.telefon.formatNumber

class CallService : InCallService() {

    // Gelen çağrı hiç yanıtlanmadan kapandıysa "cevapsız arama" bildirimi göstermek için izlenir.
    private val everActive = mutableSetOf<Call>()
    private val missedCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_ACTIVE) everActive.add(call)
            if (state == Call.STATE_DISCONNECTED) maybeNotifyMissed(call)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallState.service = this
        CallState.attach(call)
        call.registerCallback(missedCallback)

        if (call.state == Call.STATE_RINGING) {
            showIncomingNotification(call)
        }
        // Telefon kilitliyse / uygulama arka plandaysa tam ekran bildirim devreye girer.
        try {
            val opts = ActivityOptions.makeCustomAnimation(this, R.anim.fade_in, R.anim.hold)
            startActivity(callActivityIntent(), opts.toBundle())
        } catch (e: Exception) {
            // arka plan kısıtı; bildirim yeterli
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(missedCallback)
        everActive.remove(call)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        CallState.detach(call, calls.lastOrNull())
        if (calls.isEmpty()) {
            CallState.service = null
        }
    }

    /** Hiç cevaplanmamış gelen arama kapanınca "cevapsız arama" bildirimi; dokununca geri arama ekranı açılır. */
    private fun maybeNotifyMissed(call: Call) {
        val details = call.details
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        if (call in everActive) return
        // REJECTED bilerek hariç: kullanıcı aramayı kendi reddettiyse zaten görmüştür, tekrar bildirmeye gerek yok.
        val cause = details.disconnectCause?.code
        val missed = cause == DisconnectCause.MISSED || cause == DisconnectCause.CANCELED
        if (!missed) return

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(MISSED_CHANNEL_ID, "Cevapsız aramalar", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val number = details.handle?.schemeSpecificPart ?: ""
        val title = details.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: formatNumber(number).ifBlank { "Bilinmeyen numara" }

        val callBackIntent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            this, number.hashCode(), callBackIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, MISSED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("Cevapsız arama")
            .setContentText(title)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        nm.notify(MISSED_NOTIF_BASE_ID + number.hashCode(), notification)
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallState.setAudio(audioState)
    }

    override fun onDestroy() {
        CallState.clear()
        super.onDestroy()
    }

    private fun callActivityIntent() = Intent(this, InCallActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    private fun showIncomingNotification(call: Call) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Gelen aramalar", NotificationManager.IMPORTANCE_HIGH)
        )
        val pending = PendingIntent.getActivity(
            this, 0, callActivityIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val number = call.details.handle?.schemeSpecificPart ?: ""
        val title = call.details.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: formatNumber(number).ifBlank { "Bilinmeyen numara" }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(title)
            .setContentText("Gelen arama")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
        nm.notify(NOTIF_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "incoming_calls"
        private const val NOTIF_ID = 1001
        private const val MISSED_CHANNEL_ID = "missed_calls"
        private const val MISSED_NOTIF_BASE_ID = 2000
    }
}
