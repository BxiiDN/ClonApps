package com.clonapps.fixture

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Job latar belakang sederhana: mencatat waktu eksekusi di data aplikasi (per profil). */
class FixtureJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        getSharedPreferences(FixtureStore.PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_background_run", System.currentTimeMillis()).apply()
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        fun schedule(context: Context): Int {
            val js = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(42, ComponentName(context, FixtureJobService::class.java))
                .setMinimumLatency(5_000)
                .setOverrideDeadline(60_000)
                .build()
            return js.schedule(job)
        }
    }
}
