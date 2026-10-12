package com.queuego.rider

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val RIDER_ORDER_CHANNEL = "queuego_orders_v2"
private const val RIDER_ORDER_CHANNEL_NAME = "งาน QueueGo Rider"

internal fun ensureRiderOrderChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .build()
    val channel = NotificationChannel(
        RIDER_ORDER_CHANNEL,
        RIDER_ORDER_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "แจ้งเตือนงานใหม่และสถานะงานของ QueueGo Rider"
        enableVibration(true)
        setSound(sound, attributes)
    }
    manager.createNotificationChannel(channel)
}

private suspend fun firebaseToken(): String = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token ->
            if (continuation.isActive) continuation.resume(token)
        }
        .addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
}

internal fun riderFirebaseConfigured(context: Context): Boolean =
    runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

internal suspend fun syncRiderNativePush(
    context: Context,
    auth: QueueGoAuth,
    api: QueueGoApi,
    store: SessionStore
): Boolean {
    // Only an approved/active Rider may register a device for job notifications.
    // This guard is intentionally below the UI layer so pending/suspended accounts
    // cannot become push-eligible through a stale screen or lifecycle callback.
    if (auth.user.status != "active") {
        disableRiderNativePush(auth, api, store)
        return false
    }
    ensureRiderOrderChannel(context)
    if (!riderFirebaseConfigured(context)) return false
    val token = firebaseToken().trim()
    require(token.length >= 16) { "ไม่ได้รับ Push token จาก Firebase" }
    api.subscribeNativePush(auth, store.pushDeviceId(), token)
    return true
}

internal suspend fun disableRiderNativePush(
    auth: QueueGoAuth,
    api: QueueGoApi,
    store: SessionStore
) {
    runCatching { api.unsubscribeNativePush(auth, store.pushDeviceId()) }
}

class QueueGoRiderMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.length < 16) return
        val store = SessionStore(applicationContext)
        val cached = store.load() ?: return
        serviceScope.launch {
            runCatching {
                val api = QueueGoApi()
                val auth = api.validate(cached)
                store.save(auth)
                if (auth.user.status == "active") {
                    api.subscribeNativePush(auth, store.pushDeviceId(), token)
                } else {
                    disableRiderNativePush(auth, api, store)
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Foreground/data-only FCM still reaches the service even when the UI is not
        // visible. Never surface Rider job/status notifications for a cached account
        // that is no longer approved for work.
        val cached = SessionStore(applicationContext).load() ?: return
        if (cached.user.status != "active" || cached.user.role != "rider") return
        val recipientRole = message.data["role"]
        if (!recipientRole.isNullOrBlank() && recipientRole != "rider") return
        ensureRiderOrderChannel(this)

        // FCM displays notification payloads automatically while the app is backgrounded.
        // This local path is for foreground/data-only delivery so Rider receives the same alert.
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "QueueGo Rider"
        val body = message.notification?.body
            ?: message.data["message"]
            ?: "มีงานหรือสถานะใหม่ กรุณาเปิด QueueGo Rider"
        val referenceId = message.data["referenceId"]
            ?: message.data["reference_id"]
            ?: ""
        val type = message.data["type"].orEmpty()

        val recipientId = message.data["userId"]
        val recipientSession = message.data["sessionId"]
        if (!recipientId.isNullOrBlank() && recipientId != cached.user.id) return
        if (!recipientSession.isNullOrBlank() && recipientSession != cached.session.sessionId) return
        val isOffer = type == "rider_offer" || (type == "order" && title == "มีงานใหม่")
        // Firebase invokes this callback on a worker thread and keeps its wake lock
        // only until it returns. Bound the single offer read; don't detach notification work.
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.areNotificationsEnabled()) return@runBlocking
            var offerExpiry: Long? = null
            if (isOffer) {
                val publishedExpiry = riderOfferExpiryMillis(message.data["offerExpiresAt"])
                // New sender verifies the selected Rider, live lease and active app session.
                // Use its bounded metadata immediately, including when the cached JWT needs refresh.
                offerExpiry = if (type == "rider_offer" && recipientId == cached.user.id &&
                    recipientSession == cached.session.sessionId && publishedExpiry != null) publishedExpiry
                    else runCatching { QueueGoApi().liveOfferExpiry(cached, referenceId) }.getOrNull()
                if (offerExpiry == null || offerExpiry <= System.currentTimeMillis() ||
                    (publishedExpiry != null && publishedExpiry != offerExpiry)) return@runBlocking
            }
            // A reply received after logout/account switching cannot display the old account's push.
            val current = SessionStore(applicationContext).load() ?: return@runBlocking
            if (current.user.id != cached.user.id || current.session.sessionId != cached.session.sessionId ||
                current.user.role != "rider" || current.user.status != "active") return@runBlocking
            val tag = if (isOffer) "queuego-rider-offer:" + cached.user.id + ":" + referenceId
                else "queuego-rider-event:" + cached.user.id + ":" +
                    (message.data["notificationId"] ?: message.messageId ?: referenceId)
            val key = if (offerExpiry != null) riderOfferAlertKey(cached.user.id, referenceId, offerExpiry)
                else tag
            val audible = consumeRiderAlert(applicationContext, key)
            if (!isOffer && !audible) return@runBlocking
            val intent = Intent(this@QueueGoRiderMessagingService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("queuego_push_reference_id", referenceId)
                putExtra("queuego_push_type", type)
            }
            val pendingIntent = PendingIntent.getActivity(
                this@QueueGoRiderMessagingService, tag.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(this@QueueGoRiderMessagingService, RIDER_ORDER_CHANNEL)
                .setSmallIcon(R.drawable.qg_notification)
                .setContentTitle(title.take(80))
                .setContentText(body.take(220))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(220)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(if (type == "voice_call") NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
                .setOnlyAlertOnce(true)
                .setSilent(!audible)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
            offerExpiry?.let { expiry ->
                val remaining = expiry - System.currentTimeMillis()
                if (remaining <= 0L) return@runBlocking
                builder.setTimeoutAfter(remaining)
            }
            runCatching { manager.notify(tag, 0, builder.build()) }
                .onFailure { android.util.Log.w("QueueGoPush", "Notification display failed") }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

internal fun clearRiderOrderNotifications(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.activeNotifications.filter { it.notification.channelId == RIDER_ORDER_CHANNEL }
        .forEach { manager.cancel(it.tag, it.id) }
}

internal fun reconcileRiderOfferNotifications(context: Context, userId: String, orderId: String?) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val prefix = "queuego-rider-offer:$userId:"
    val current = orderId?.let { prefix + it }
    manager.activeNotifications.filter { it.tag?.startsWith(prefix) == true && it.tag != current }
        .forEach { manager.cancel(it.tag, it.id) }
}
