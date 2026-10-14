package com.queuego.ridermaplibreosm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

class QueueGoPmTilesDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME)
            ?.takeIf(::safeFileName)
            ?: return Result.failure()
        val expectedSha256 = inputData.getString(KEY_EXPECTED_SHA256)

        val destination = File(applicationContext.filesDir, "queuego-maps/$fileName")
        return runCatching {
            QueueGoPmTilesDownloader().download(
                sourceUrl = sourceUrl,
                destination = destination,
                expectedSha256 = expectedSha256
            ) { progress ->
                setProgressAsync(
                    workDataOf(
                        KEY_DOWNLOADED_BYTES to progress.downloadedBytes,
                        KEY_TOTAL_BYTES to (progress.totalBytes ?: -1L)
                    )
                )
            }
        }.fold(
            onSuccess = {
                Result.success(
                    workDataOf(
                        KEY_LOCAL_PATH to destination.absolutePath,
                        KEY_DOWNLOADED_BYTES to destination.length(),
                        KEY_TOTAL_BYTES to destination.length()
                    )
                )
            },
            onFailure = { error ->
                if (runAttemptCount < MAX_RETRY_COUNT) Result.retry()
                else Result.failure(
                    workDataOf(KEY_ERROR to (error.message ?: error.javaClass.simpleName))
                )
            }
        )
    }

    companion object {
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_EXPECTED_SHA256 = "expected_sha256"
        const val KEY_LOCAL_PATH = "local_path"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"

        private const val MAX_RETRY_COUNT = 5

        private fun safeFileName(value: String): Boolean =
            value.endsWith(".pmtiles") &&
                value.length in 9..120 &&
                value.all { it.isLetterOrDigit() || it in ".-_" }

        fun enqueue(
            context: Context,
            sourceUrl: String,
            fileName: String = "buriram-shortbread.pmtiles",
            expectedSha256: String? = null
        ) = WorkManager.getInstance(context).enqueueUniqueWork(
            "queuego-pmtiles-$fileName",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<QueueGoPmTilesDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(
                    Data.Builder()
                        .putString(KEY_SOURCE_URL, sourceUrl)
                        .putString(KEY_FILE_NAME, fileName)
                        .apply {
                            expectedSha256?.let { putString(KEY_EXPECTED_SHA256, it) }
                        }
                        .build()
                )
                .build()
        )
    }
}
