package com.example.jobfilter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.concurrent.Executors

class JobListener : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var lastSig = ""
    private var lastTime = 0L
    private var nid = 1000
    // satu thread berasingan: carian koordinat (rangkaian) tak sekat, dan susunan job kekal
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("jobs", "Job lulus filter", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ms", "MY")
            ttsReady = true
        }
    }

    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, JobListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        if (!pkg.contains("lalamove", true) && !pkg.contains("huolala", true)) return
        if (!Prefs(this).enabled) return

        val ex = sbn.notification.extras
        val job = Parser.parse(
            ex.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        ) ?: return

        // elak dua kali untuk notifikasi sama (update)
        val sig = "${job.tag}|${job.pickup}|${job.drop}|${job.price}"
        val now = System.currentTimeMillis()
        if (sig == lastSig && now - lastTime < 8000) return
        lastSig = sig; lastTime = now

        val open: PendingIntent? = sbn.notification.contentIntent
            ?: packageManager.getLaunchIntentForPackage(pkg)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        worker.execute { try { handle(job, open, sig) } catch (e: Exception) { } }
    }

    private fun handle(job: Job, open: PendingIntent?, sig: String) {
        val prefs = Prefs(this)

        // ---- job mahal: RM mutlak, atau RM per km (jarak anggaran) ----
        var premium = false
        var km: Double? = null
        if (prefs.premiumOn) {
            if (prefs.premAbs > 0f && job.price >= prefs.premAbs) premium = true
            if (!premium && prefs.premPerKm > 0f) {
                km = Geo.distanceKm(this, job.pickup, job.drop, prefs.roadFactor.toDouble())
                if (km != null && km >= 0.5 && job.price / km >= prefs.premPerKm) premium = true
            }
        }

        val reason = JobFilter.check(job, prefs, premium)
        val fav = JobFilter.isFav(job, prefs)
        LogDb.get(this).add(job, reason, fav, km, premium)
        if (reason != null) return

        val price = String.format(Locale.US, "%.2f", job.price)
        val kind = if (job.scheduled) "Scheduled" else "Immediate"

        if (prefs.notify) {
            var body = "${job.pickup} > ${job.drop}"
            if (km != null && km >= 0.5) {
                body += "\n~" + String.format(Locale.US, "%.1f", km) + " km, RM" + String.format(Locale.US, "%.1f", job.price / km) + "/km"
            }
            val n = NotificationCompat.Builder(this, "jobs")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("${if (premium) "MAHAL " else ""}${if (fav) "★ " else ""}RM$price  $kind")
                .setContentText(body.substringBefore('\n'))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(nid++, n)
        }
        if (prefs.speak && ttsReady) {
            // sebut ringgit sahaja (tanpa sen); QUEUE_ADD = beratur bila job masuk laju
            val ringgit = Math.round(job.price)
            val say = "${if (premium) "Mahal. " else ""}${if (fav) "Favorit. " else ""}${job.pickup}. ${job.drop}. $ringgit ringgit"
            tts?.setSpeechRate(prefs.speechRate)
            tts?.speak(Speech.fix(say), TextToSpeech.QUEUE_ADD, null, sig)
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        tts?.shutdown()
        super.onDestroy()
    }
}
