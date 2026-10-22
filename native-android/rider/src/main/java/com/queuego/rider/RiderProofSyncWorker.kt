package com.queuego.rider

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal data class RiderProofSyncRequest(
    val userId: String,
    val workId: String,
    val mode: String,
    val photoUri: String,
    val marketPickupId: String? = null,
    val marketOrderId: String? = null,
    val marketPickupAmount: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    init {
        require(userId.isNotBlank())
        require(workId.isNotBlank())
        require(mode in setOf("pickup", "delivery", "marketPickup", "expressPickup", "expressDelivery"))
        require(photoUri.isNotBlank())
        require((latitude == null) == (longitude == null))
        if (mode == "marketPickup") {
            require(!marketPickupId.isNullOrBlank())
            require(marketPickupAmount != null && marketPickupAmount.isFinite() && marketPickupAmount >= 0.0)
        }
    }

    val uniqueName: String
        get() = buildString {
            append("qg-rider-proof-sync:")
            append(userId).append(':').append(workId).append(':').append(mode)
            marketPickupId?.let { append(':').append(it) }
        }

    fun toData(): Data = Data.Builder()
        .putString("user_id", userId)
        .putString("work_id", workId)
        .putString("mode", mode)
        .putString("photo_uri", photoUri)
        .apply {
            marketPickupId?.let { putString("market_pickup_id", it) }
            marketOrderId?.let { putString("market_order_id", it) }
            marketPickupAmount?.let { putDouble("market_pickup_amount", it) }
            latitude?.let { putDouble("latitude", it) }
            longitude?.let { putDouble("longitude", it) }
        }
        .build()

    companion object {
        fun from(data: Data): RiderProofSyncRequest? = runCatching {
            RiderProofSyncRequest(
                userId = requireNotNull(data.getString("user_id")),
                workId = requireNotNull(data.getString("work_id")),
                mode = requireNotNull(data.getString("mode")),
                photoUri = requireNotNull(data.getString("photo_uri")),
                marketPickupId = data.getString("market_pickup_id"),
                marketOrderId = data.getString("market_order_id"),
                marketPickupAmount = data.getDouble("market_pickup_amount", Double.NaN)
                    .takeIf { it.isFinite() },
                latitude = data.getDouble("latitude", Double.NaN).takeIf { it.isFinite() },
                longitude = data.getDouble("longitude", Double.NaN).takeIf { it.isFinite() }
            )
        }.getOrNull()
    }
}

internal object RiderProofSyncScheduler {
    private const val FOREGROUND_GRACE_SECONDS = 10L

    fun enqueue(context: Context, request: RiderProofSyncRequest) {
        val work = OneTimeWorkRequestBuilder<RiderProofSyncWorker>()
            .setInputData(request.toData())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            // Let the foreground mutation win normally. This request is the crash/offline safety net.
            .setInitialDelay(FOREGROUND_GRACE_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            request.uniqueName,
            ExistingWorkPolicy.KEEP,
            work
        )
    }

    fun cancel(context: Context, request: RiderProofSyncRequest) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(request.uniqueName)
    }
}

internal class RiderProofSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val request = RiderProofSyncRequest.from(inputData) ?: return Result.failure()
        val sessionStore = SessionStore(applicationContext)
        val savedAuth = sessionStore.load() ?: return Result.retry()
        if (savedAuth.user.id != request.userId) return Result.failure()

        val api = QueueGoApi()
        val auth = try {
            api.validate(savedAuth).also(sessionStore::save)
        } catch (_: Throwable) {
            return retryOrFail()
        }
        if (auth.user.id != request.userId || auth.user.status != "active") return Result.failure()

        val uri = Uri.parse(request.photoUri)
        val mutation = runCatching {
            when (request.mode) {
                "pickup" -> api.pickupWithPhoto(
                    applicationContext,
                    auth,
                    request.workId,
                    uri,
                    request.latitude,
                    request.longitude
                )
                "delivery" -> api.completeWithPhoto(
                    applicationContext,
                    auth,
                    request.asJob(status = "in_progress", vertical = "food"),
                    uri,
                    request.latitude,
                    request.longitude
                )
                "marketPickup" -> api.marketPickupWithPhoto(
                    applicationContext,
                    auth,
                    MarketPickup(
                        pickupId = requireNotNull(request.marketPickupId),
                        status = "READY",
                        shopName = "ร้านค้า",
                        shopAddress = null,
                        shopAmount = requireNotNull(request.marketPickupAmount),
                        latitude = null,
                        longitude = null
                    ),
                    uri,
                    request.latitude,
                    request.longitude
                )
                "expressPickup" -> api.expressPickupWithPhoto(
                    applicationContext,
                    auth,
                    request.asJob(status = "rider_assigned", vertical = "express"),
                    uri,
                    request.latitude,
                    request.longitude
                )
                "expressDelivery" -> api.expressCompleteWithPhoto(
                    applicationContext,
                    auth,
                    request.asJob(status = "in_progress", vertical = "express"),
                    uri,
                    request.latitude,
                    request.longitude
                )
                else -> error("unsupported proof sync mode")
            }
        }
        if (mutation.isFailure) return retryOrFail()

        val proofStore = RiderPendingProofStore(applicationContext)
        runCatching {
            proofStore.clearConfirmedMatching(
                userId = request.userId,
                slot = RIDER_PROOF_SLOT_DELIVERY,
                workId = request.workId,
                mode = request.mode,
                marketPickupId = request.marketPickupId
            )
            proofStore.clearVerification(request.userId)
        }
        return Result.success()
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < 12) Result.retry() else Result.failure()

    private fun RiderProofSyncRequest.asJob(status: String, vertical: String): RiderJob =
        RiderJob(
            id = workId,
            orderNumber = null,
            status = status,
            pickupAddress = null,
            pickupLat = null,
            pickupLng = null,
            deliveryAddress = null,
            deliveryLat = null,
            deliveryLng = null,
            deliveryFee = null,
            marketOrderId = marketOrderId,
            arrivedShopAt = null,
            arrivedCustomerAt = null,
            fulfillmentVertical = vertical
        )
}
