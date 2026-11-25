package bd.bartaguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import bd.bartaguard.engine.Verdict
import java.util.concurrent.Executors

/**
 * Opt-in: the user grants notification access in system settings and turns "Auto-check" on.
 * Reads the text of new notifications from messaging apps, checks it on the phone, and posts a
 * warning only when a message looks like a scam. Nothing is uploaded; nothing is stored unless the
 * user turned history on. We never read the SMS inbox (no READ_SMS permission).
 */
class WatchService : NotificationListenerService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val recent = ArrayDeque<Int>()

    private val messagingApps = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca", "com.facebook.mlite",
        "org.telegram.messenger", "com.imo.android.imoim", "com.viber.voip",
    )

    private fun watched(pkg: String) = pkg in messagingApps || pkg == Telephony.Sms.getDefaultSmsPackage(this)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || !watched(sbn.packageName)) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val text = messageText(n.extras)?.takeIf { it.length >= 8 } ?: return
        val key = text.hashCode()
        synchronized(recent) {
            if (key in recent) return // apps re-post the same message when the conversation updates
            recent.addLast(key); if (recent.size > 64) recent.removeFirst()
        }
        val prefs = Prefs(this)
        if (!prefs.watch) return
        worker.execute {
            val a = Engines.get(this).analyze(text)
            prefs.save(a.verdict, text, appName(sbn.packageName))
            if (a.verdict == Verdict.SUSPICIOUS) warn(text, sbn.packageName, a.flags.firstOrNull()?.let { if (prefs.lang == "en") it.en else it.bn })
        }
    }

    /** The newest message in a MessagingStyle notification, else the expanded text, else the text. */
    private fun messageText(ex: Bundle): String? {
        @Suppress("DEPRECATION")
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        val last = (msgs?.lastOrNull() as? Bundle)?.getCharSequence("text")
        return (last ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()
    }

    private fun appName(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) { pkg }

    private fun warn(text: String, pkg: String, reason: String?) {
        val bn = Prefs(this).lang != "en"
        ensureChannel(this, bn)
        val open = PendingIntent.getActivity(this, text.hashCode(),
            Intent(this, MainActivity::class.java).putExtra(Intent.EXTRA_TEXT, text).putExtra(MainActivity.EXTRA_FROM_WATCH, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (bn) "${appName(pkg)}-এর মেসেজটি প্রতারণা হতে পারে" else "Possible scam message in ${appName(pkg)}"
        val body = reason ?: if (bn) "উত্তর দেবেন না, লিংকে যাবেন না, কোড বা টাকা পাঠাবেন না।" else "Don't reply, open links, or send codes or money."
        val note = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(text.hashCode(), note)
        }
    }

    override fun onDestroy() { worker.shutdown(); super.onDestroy() }

    companion object {
        const val CHANNEL = "scam_warnings"

        fun ensureChannel(ctx: Context, bn: Boolean) {
            val ch = NotificationChannel(CHANNEL, if (bn) "প্রতারণার সতর্কতা" else "Scam warnings", NotificationManager.IMPORTANCE_HIGH)
            ch.description = if (bn) "সন্দেহজনক মেসেজ এলে জানায়" else "Alerts when an incoming message looks like a scam"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        fun hasAccess(ctx: Context) = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)
    }
}
