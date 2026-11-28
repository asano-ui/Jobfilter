package com.example.jobfilter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class JobListener : NotificationListenerService(), TextToSpeech.OnInitListener {
    companion object {
        const val CH_JOBS = "jobs"
        const val CH_STATUS = "status"
        const val CH_ALERT = "alert"
        const val ACT_TOGGLE = "com.example.jobfilter.TOGGLE"
        const val ACT_ABAI = "com.example.jobfilter.ABAI"
        const val WINDOW = 15 * 60 * 1000L   // job sama dianggap baru selepas 15 minit tanpa notifikasi
        const val MUTE = 30 * 60 * 1000L     // "Abai": senyap laluan ini 30 minit
        const val KM_WORD = "kilo meter"      // ejaan untuk suara; "kilometer" dieja huruf demi huruf oleh sesetengah suara
        val muted = ConcurrentHashMap<String, Long>()
    }

    private class Seen(val price: Double, val time: Long)

    private val seen = HashMap<String, Seen>()
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var nid = 1000
    // satu thread berasingan: carian koordinat (rangkaian) tak sekat, dan susunan job kekal
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener

    private val nm get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        nm.createNotificationChannel(NotificationChannel(CH_JOBS, "Job lulus filter", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "Status Job Filter", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Amaran terputus", NotificationManager.IMPORTANCE_HIGH))
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "enabled") {
                updateStatus()
                if (!Prefs(this).enabled) tts?.stop()
            }
        }
        getSharedPreferences("cfg", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ms", "MY")
            ttsReady = true
        }
    }

    // ---- status tetap + butang Jeda/Sambung ----
    private fun updateStatus() {
        val on = Prefs(this).enabled
        val toggle = PendingIntent.getBroadcast(
            this, 1, Intent(this, ActionReceiver::class.java).setAction(ACT_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openApp = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CH_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (on) "Job Filter: aktif" else "Job Filter: dijeda")
            .setContentText(if (on) "Membaca job Lalamove" else "Tiada job dibaca sehingga disambung")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, if (on) "Jeda" else "Sambung", toggle)
            .build()
        nm.notify(1, n)
    }

    override fun onListenerConnected() {
        nm.cancel(2)          // buang amaran terputus
        updateStatus()
    }

    // ---- amaran bila terputus ----
    override fun onListenerDisconnected() {
        nm.cancel(1)
        val fix = PendingIntent.getActivity(this, 3, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            2, NotificationCompat.Builder(this, CH_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Job Filter terputus")
                .setContentText("Job tidak dibaca. Tekan untuk aktifkan semula akses notifikasi.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(fix)
                .build()
        )
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

        val key = "${job.pickup}|${job.drop}|${job.scheduled}".lowercase()
        val now = System.currentTimeMillis()

        // "Abai": laluan ini disenyapkan
        val mu = muted[key]
        if (mu != null && now - mu < MUTE) return

        // job sama dibaca semula hanya bila jumlah (bonus) naik
        val prev = seen[key]
        val repeat = prev != null && now - prev.time < WINDOW
        val up = !repeat || job.price > prev!!.price + 0.001
        seen[key] = Seen(if (repeat) maxOf(prev!!.price, job.price) else job.price, now)
        if (seen.size > 300) seen.entries.removeAll { now - it.value.time > WINDOW }
        if (!up) return

        val open: PendingIntent? = sbn.notification.contentIntent
            ?: packageManager.getLaunchIntentForPackage(pkg)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        val sig = "$key|${job.price}"
        worker.execute { try { handle(job, open, key, sig) } catch (e: Exception) { } }
    }

    private fun handle(job: Job, open: PendingIntent?, key: String, sig: String) {
        val prefs = Prefs(this)

        // ---- job mahal: RM mutlak, atau RM per km (jarak anggaran) ----
        var premium = false
        var km: Double? = null
        if (prefs.premiumOn) {
            if (prefs.premAbs > 0f && job.price >= prefs.premAbs) premium = true
            // jarak dikira juga bila mahal ikut RM mutlak, supaya RM per km boleh disebut
            if (premium || prefs.premPerKm > 0f) km = Geo.distanceKm(this, job.pickup, job.drop, prefs.roadFactor.toDouble())
            if (!premium && km != null && km >= 0.5 && prefs.premPerKm > 0f && job.price / km >= prefs.premPerKm) premium = true
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
            val id = nid++
            val abai = PendingIntent.getBroadcast(
                this, id,
                Intent(this, ActionReceiver::class.java).setAction(ACT_ABAI).putExtra("nid", id).putExtra("key", key),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val b = NotificationCompat.Builder(this, CH_JOBS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("${if (premium) "MAHAL " else ""}${if (fav) "★ " else ""}RM$price  $kind")
                .setContentText(body.substringBefore('\n'))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
            if (open != null) b.addAction(0, "Buka", open)
            b.addAction(0, "Abai", abai)
            nm.notify(id, b.build())
        }
        if (prefs.speak && ttsReady) {
            // tanpa sen dan tanpa perkataan ringgit; QUEUE_ADD = beratur bila job masuk laju
            val value = Math.round(job.price)
            // susunan sama untuk semua job: nilai RM, lokasi; job mahal tambah RM per kilometer
            // (kalau jarak tak diketahui: "Mahal"); favorit disebut di hujung
            val tail = if (premium) {
                ". " + (if (km != null && km >= 0.5) "${Math.round(job.price / km)} per $KM_WORD" else "Mahal")
            } else ""
            val say = "$value. ${job.pickup}. ${job.drop}$tail${if (fav) ". Favorit" else ""}"
            tts?.setSpeechRate(prefs.speechRate)
            tts?.speak(Speech.fix(say), TextToSpeech.QUEUE_ADD, null, sig)
        }
    }

    override fun onDestroy() {
        getSharedPreferences("cfg", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        worker.shutdown()
        tts?.shutdown()
        super.onDestroy()
    }
}
