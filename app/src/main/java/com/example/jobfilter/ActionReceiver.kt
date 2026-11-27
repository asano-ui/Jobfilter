package com.example.jobfilter

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Butang dalam notifikasi: Jeda/Sambung (status) dan Abai (job). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            JobListener.ACT_TOGGLE -> { val p = Prefs(ctx); p.enabled = !p.enabled }
            JobListener.ACT_ABAI -> {
                JobListener.muted[i.getStringExtra("key") ?: ""] = System.currentTimeMillis()
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(i.getIntExtra("nid", -1))
            }
        }
    }
}
