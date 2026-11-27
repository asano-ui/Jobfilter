package com.example.jobfilter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class JobListener : NotificationListenerService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastSig = ""
    private var lastTime = 0L
    private var nid = 1000

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
        requestRebind(android.content.ComponentName(this, JobListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        if (!pkg.contains("lalamove", true) && !pkg.contains("huolala", true)) return
        val prefs = Prefs(this)
        if (!prefs.enabled) return

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

        val reason = JobFilter.check(job, prefs)
        prefs.addLog(job, reason)
        if (reason != null) return

        val price = if (job.price % 1.0 == 0.0) job.price.toInt().toString() else "%.2f".format(job.price)
        if (prefs.notify) {
            val n = NotificationCompat.Builder(this, "jobs")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("RM$price  [${job.tag}]")
                .setContentText("${job.pickup} > ${job.drop}")
                .setStyle(NotificationCompat.BigTextStyle().bigText("${job.pickup} > ${job.drop}"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(nid++, n)
        }
        if (prefs.speak && ttsReady) {
            // QUEUE_ADD = beratur bila notifikasi masuk laju
            tts?.speak("${job.pickup}. ${job.drop}. $price ringgit", TextToSpeech.QUEUE_ADD, null, sig)
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
