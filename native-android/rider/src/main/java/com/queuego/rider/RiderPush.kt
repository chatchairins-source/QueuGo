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
private const val RIDER_PUSH_PREFS = "queuego_rider_push_preferences"
private const val RIDER_PUSH_ENABLED = "push_enabled"

internal fun riderPushEnabled(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(RIDER_PUSH_PREFS, Context.MODE_PRIVATE)
        .getBoolean(RIDER_PUSH_ENABLED, true)

internal fun setRiderPushEnabled(context: Context, enabled: Boolean) {
    context.applicationContext
        .getSharedPreferences(RIDER_PUSH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(RIDER_PUSH_ENABLED, enabled)
        .apply()
}

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
        vibrationPattern = longArrayOf(0L, 360L, 160L, 360L)
        setShowBadge(true)
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
    if (!riderPushEnabled(context)) return false
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
        if (!riderPushEnabled(applicationContext)) return
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
        if (cached.user.status != "active") return
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

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("queuego_push_reference_id", referenceId)
            putExtra("queuego_push_type", message.data["type"].orEmpty())
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            referenceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, RIDER_ORDER_CHANNEL)
            .setSmallIcon(R.drawable.qg_notification)
            .setContentTitle(title.take(80))
            .setContentText(body.take(220))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(220)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0L, 360L, 160L, 360L))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            (message.messageId ?: referenceId.ifBlank { System.currentTimeMillis().toString() }).hashCode(),
            notification
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
