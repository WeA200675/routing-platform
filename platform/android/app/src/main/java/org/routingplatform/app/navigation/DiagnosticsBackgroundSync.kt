package org.routingplatform.app.navigation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Persistent background drain. No foreground activity or navigation process required. */
class DiagnosticsBackgroundSync(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("diagnostics-sync-config", Context.MODE_PRIVATE)
        val url = prefs.getString("endpoint", null) ?: return Result.retry()
        val queue = NavigationDiagnosticsUploadQueue(applicationContext)
        val uploader = NavigationDiagnosticsUploadWorker(queue)
        repeat(10) {
            when (uploader.uploadNext(url)) {
                NavigationDiagnosticsUploadResult.NothingPending -> return Result.success()
                is NavigationDiagnosticsUploadResult.Failed -> return Result.retry()
                is NavigationDiagnosticsUploadResult.Uploaded -> Unit
            }
        }
        return if (queue.size() == 0) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "diagnostics-sync-periodic"
        private const val IMMEDIATE = "diagnostics-sync-immediate"

        fun schedule(context: Context, endpoint: String) {
            if (!endpoint.startsWith("https://")) return
            val prefs = context.getSharedPreferences("diagnostics-sync-config", Context.MODE_PRIVATE)
            if (!prefs.edit().putString("endpoint", endpoint.trimEnd('/')).commit()) return
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val periodic = PeriodicWorkRequestBuilder<DiagnosticsBackgroundSync>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
            val immediate = OneTimeWorkRequestBuilder<DiagnosticsBackgroundSync>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            manager.enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.KEEP, immediate)
        }
    }
}
