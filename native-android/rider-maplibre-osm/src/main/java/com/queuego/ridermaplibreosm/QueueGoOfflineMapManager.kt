package com.queuego.ridermaplibreosm

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID

class QueueGoOfflineMapManager(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun enqueue(packageInfo: OfflineMapPackage): UUID {
        val request = request(packageInfo)
        workManager.enqueueUniqueWork(
            uniqueWorkName(packageInfo.id),
            ExistingWorkPolicy.KEEP,
            request
        )
        return request.id
    }

    fun installedSourceUri(packageInfo: OfflineMapPackage): String? =
        packageInfo.finalFile(appContext)
            .takeIf { it.isFile && it.length() > 0L }
            ?.let { packageInfo.installedSourceUri(appContext) }

    fun remove(packageInfo: OfflineMapPackage): Boolean {
        workManager.cancelUniqueWork(uniqueWorkName(packageInfo.id))
        val partial = packageInfo.partialFile(appContext)
        val final = packageInfo.finalFile(appContext)
        val partialDeleted = !partial.exists() || partial.delete()
        val finalDeleted = !final.exists() || final.delete()
        return partialDeleted && finalDeleted
    }

    private fun request(packageInfo: OfflineMapPackage): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<OfflinePmTilesDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    OFFLINE_INPUT_ID to packageInfo.id,
                    OFFLINE_INPUT_URL to packageInfo.downloadUrl,
                    OFFLINE_INPUT_SHA256 to packageInfo.sha256.orEmpty()
                )
            )
            .addTag("queuego-offline-map")
            .addTag("queuego-offline-map:" + packageInfo.id)
            .build()

    private fun uniqueWorkName(id: String): String = "queuego-offline-map:" + id
}
