package com.nemesis.apk

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Best-effort recovery if Android stops the sticky service while the app is idle.
 * Android may defer periodic jobs or block foreground starts; no availability guarantee. */
class RuntimeRecoveryJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        try { startForegroundService(Intent(this,NemesisService::class.java)) }
        catch(e:Exception) { android.util.Log.w("NEMESIS", "Redémarrage différé par Android",e) }
        return false
    }
    override fun onStopJob(params: JobParameters?): Boolean = true
    companion object {
        const val ID=4102
        @Synchronized fun schedule(context:Context) {
            val scheduler=context.getSystemService(JobScheduler::class.java)
            // Replacing a running periodic job resets its interval and can cause
            // a tight restart loop. Keep the already scheduled job intact.
            val existing=scheduler.getPendingJob(ID)
            if(existing!=null && existing.flexMillis==5*60*1000L) return
            if(existing!=null) scheduler.cancel(ID)
            val job=JobInfo.Builder(ID,ComponentName(context,RuntimeRecoveryJob::class.java))
                .setPeriodic(15*60*1000L,5*60*1000L).setPersisted(true).build()
            scheduler.schedule(job)
        }
    }
}
