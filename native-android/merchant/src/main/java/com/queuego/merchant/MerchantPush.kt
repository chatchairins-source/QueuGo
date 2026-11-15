package com.queuego.merchant

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
import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeAuthApi
import com.queuego.shared.NativePushApi
import com.queuego.shared.NativePushDeviceStore
import com.queuego.shared.SecureRoleSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Firebase token callbacks may finish after logout or account switching.
internal fun merchantPushSessionCurrent(
    validated: NativeAuth,
    current: NativeAuth?,
    nowMs: Long
): Boolean =
    current != null &&
        validated.user.role == "shop" &&
        validated.user.status == "active" &&
        current.user.role == "shop" &&
        current.user.status == "active" &&
        validated.user.id.isNotBlank() &&
        current.user.id.isNotBlank() &&
        validated.user.authUserId.isNotBlank() &&
        current.user.authUserId.isNotBlank() &&
        validated.session.sessionId.isNotBlank() &&
        current.session.sessionId.isNotBlank() &&
        current.user.id == validated.user.id &&
        validated.user.authUserId == validated.session.authUserId &&
        current.user.authUserId == current.session.authUserId &&
        current.user.authUserId == validated.user.authUserId &&
        current.session.authUserId == validated.session.authUserId &&
        current.session.sessionId == validated.session.sessionId &&
        validated.session.accessToken.isNotBlank() &&
        current.session.accessToken == validated.session.accessToken &&
        validated.session.expiresAtMs > nowMs &&
        current.session.expiresAtMs > nowMs

internal fun merchantPushNotificationSessionActive(auth: NativeAuth, nowMs: Long): Boolean =
    auth.user.role == "shop" &&
        auth.user.status == "active" &&
        auth.user.id.isNotBlank() &&
        auth.user.authUserId.isNotBlank() &&
        auth.session.authUserId == auth.user.authUserId &&
        auth.session.sessionId.isNotBlank() &&
        auth.session.accessToken.isNotBlank() &&
        auth.session.expiresAtMs > nowMs

// FCM registration tokens are opaque, non-whitespace values; never send malformed payloads.
internal fun merchantUsableFcmToken(raw: String): String? =
    raw.trim().takeIf { token ->
        token.length >= 16 && token.none { it.isWhitespace() || it.isISOControl() }
    }

// Skip Auth validation and token registration for a cached session that is already invalid.
internal fun merchantPushRefreshPreflight(auth: NativeAuth, rawToken: String, nowMs: Long): Boolean =
    merchantUsableFcmToken(rawToken) != null && merchantPushNotificationSessionActive(auth, nowMs)

// A token can remain registered briefly after a shop switch. Drop messages explicitly
// addressed to another shop instead of showing its order details to the new account.
// Legacy messages without an audience field retain their existing behavior.
internal fun merchantPushTargetsShop(data: Map<String, String>, currentShopId: String): Boolean {
    if (currentShopId.isBlank()) return false
    val audiences = listOf("shopId", "shop_id", "merchantId", "merchant_id")
        .mapNotNull { key -> data[key] }
    return audiences.all { it.isNotBlank() && it == currentShopId }
}

// Conflicting payload aliases must never route a notification tap to an arbitrary order.
internal fun merchantPushReferenceId(data: Map<String, String>): String? {
    val references = listOfNotNull(data["referenceId"], data["reference_id"])
    // Order references are opaque identifiers, not free-form display text. Reject malformed
    // values rather than forwarding invisible controls, Unicode format characters or whitespace to the order screen.
    if (references.any { it.isBlank() || it.any { char -> char.isWhitespace() || char.isISOControl() || Character.getType(char) == Character.FORMAT } }) return null
    if (references.distinct().size > 1) return null
    return references.firstOrNull().orEmpty()
}

internal fun merchantPushIntentRequestCode(shopId: String, messageKey: String): Int =
    ("shop:" + shopId + "|message:" + messageKey).hashCode()

// Use one identity for the tap PendingIntent and posted notification. Missing FCM IDs
// must not reuse an old tap target when two unrelated notifications arrive.
internal fun merchantPushMessageKey(messageId: String?, referenceId: String, type: String, fallbackId: String): String =
    messageId?.takeIf { it.isNotBlank() }
        ?: referenceId.takeIf { it.isNotBlank() }?.let { "reference:$it|type:$type" }
        ?: "unidentified:$fallbackId"

internal const val MERCHANT_NOTIFICATION_CHANNEL = "queuego_orders_v2"

internal fun ensureMerchantNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
    manager.createNotificationChannel(
        NotificationChannel(
            MERCHANT_NOTIFICATION_CHANNEL,
            "การแจ้งเตือน QueueGo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "ออเดอร์ สถานะการจัดส่ง แชต และสายเรียกเข้า QueueGo"
            enableVibration(true)
            setSound(sound, attributes)
        }
    )
}

internal fun merchantFirebaseConfigured(context: Context): Boolean =
    runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

private suspend fun merchantFirebaseToken(): String = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token -> if (continuation.isActive) continuation.resume(token) }
        .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
}

internal suspend fun syncMerchantNativePush(context: Context, auth: NativeAuth): Boolean {
    val store = NativePushDeviceStore(context, "shop")
    val api = NativePushApi()
    if (auth.user.role != "shop" || auth.user.status != "active") {
        runCatching { api.unsubscribe(auth, store.deviceId()) }
        return false
    }
    // Do not request a Firebase token for an expired or unbound cached identity.
    // The session is checked again after the suspending Firebase call below.
    if (!merchantPushNotificationSessionActive(auth, System.currentTimeMillis())) return false
    ensureMerchantNotificationChannel(context)
    if (!merchantFirebaseConfigured(context)) return false
    val token = merchantUsableFcmToken(merchantFirebaseToken())
        ?: error("ไม่ได้รับ Push token จาก Firebase")
    // Firebase token retrieval suspends: logout or account switching can happen meanwhile.
    val current = SecureRoleSessionStore(context, "shop").load()
    if (!merchantPushSessionCurrent(auth, current, System.currentTimeMillis())) return false
    api.subscribe(auth, store.deviceId(), token)
    return true
}

internal suspend fun disableMerchantNativePush(context: Context, auth: NativeAuth) {
    runCatching {
        NativePushApi().unsubscribe(auth, NativePushDeviceStore(context, "shop").deviceId())
    }
}

class QueueGoMerchantMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val usableToken = merchantUsableFcmToken(token) ?: return
        val sessionStore = SecureRoleSessionStore(applicationContext, "shop")
        val cached = sessionStore.load() ?: return
        if (!merchantPushRefreshPreflight(cached, usableToken, System.currentTimeMillis())) return
        serviceScope.launch {
            runCatching {
                val auth = NativeAuthApi().validate(cached, "shop") { sessionStore.save(it) }
                if (merchantPushSessionCurrent(auth, sessionStore.load(), System.currentTimeMillis())) {
                    NativePushApi().subscribe(
                        auth,
                        NativePushDeviceStore(applicationContext, "shop").deviceId(),
                        usableToken
                    )
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val cached = SecureRoleSessionStore(applicationContext, "shop").load() ?: return
        if (!merchantPushNotificationSessionActive(cached, System.currentTimeMillis())) return
        if (!merchantPushTargetsShop(message.data, cached.user.id)) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        ensureMerchantNotificationChannel(this)
        val title = message.notification?.title ?: message.data["title"] ?: "QueueGo"
        val body = message.notification?.body ?: message.data["message"] ?: "มีรายการอัปเดตใน QueueGo"
        val referenceId = merchantPushReferenceId(message.data) ?: return
        val type = message.data["type"].orEmpty()
        val notificationId = merchantPushIntentRequestCode(
            cached.user.id,
            merchantPushMessageKey(message.messageId, referenceId, type, System.nanoTime().toString())
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("queuego_push_reference_id", referenceId)
            putExtra("queuego_push_type", type)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, MERCHANT_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.qg_notification)
            .setContentTitle(title.take(80))
            .setContentText(body.take(220))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(220)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (type == "voice_call") NotificationCompat.CATEGORY_CALL
                else NotificationCompat.CATEGORY_MESSAGE
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                notificationId,
                notification
            )
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
