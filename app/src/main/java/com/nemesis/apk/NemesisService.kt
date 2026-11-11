package com.nemesis.apk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Process priority while the embedded provider is available; Android may still stop it. */
class NemesisService : Service() {
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val watchdog=object:Runnable {
        override fun run() {
            try { com.nemap.android.EmbeddedRuntime.start(applicationContext) }
            catch(e:Throwable) { android.util.Log.e("NEMESIS", "NemApi indisponible, nouvelle tentative",e) }
            handler.postDelayed(this,15_000)
        }
    }
    override fun onDestroy() { handler.removeCallbacks(watchdog); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nemesis_runtime", "NEMESIS actif", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "nemesis_runtime").setContentTitle("NEMESIS actif")
            .setContentText("NemApi est disponible pour les conversations et les tâches.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setOngoing(true).setContentIntent(open).build()
        startForeground(4101, n)
        RuntimeRecoveryJob.schedule(applicationContext)
        handler.removeCallbacks(watchdog); handler.post(watchdog)
        return START_STICKY
    }
}
