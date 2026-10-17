package com.ozel.telefon.call

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.ozel.telefon.R
import com.ozel.telefon.formatNumber

class CallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallState.service = this
        CallState.attach(call)

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
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        CallState.detach(call, calls.lastOrNull())
        if (calls.isEmpty()) {
            CallState.service = null
        }
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
    }
}
