package com.queuego.shared

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val QUEUEGO_NATIVE_PUSH_CHANNEL = "queuego_orders"

fun nativePushPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

fun ensureNativePushChannel(
    context: Context,
    channelName: String,
    description: String,
    highImportance: Boolean
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .build()
    val channel = NotificationChannel(
        QUEUEGO_NATIVE_PUSH_CHANNEL,
        channelName,
        if (highImportance) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        this.description = description
        enableVibration(true)
        setSound(sound, attributes)
    }
    manager.createNotificationChannel(channel)
}

fun nativeFirebaseConfigured(context: Context): Boolean =
    runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

private suspend fun nativeFirebaseToken(): String = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token ->
            if (continuation.isActive) continuation.resume(token)
        }
        .addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
}

suspend fun subscribeNativePush(
    auth: NativeAuth,
    pushDeviceId: String,
    token: String,
    http: QueueGoNativeApi = QueueGoNativeApi()
) {
    require(pushDeviceId.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
        "รหัสอุปกรณ์แจ้งเตือนไม่ถูกต้อง"
    }
    val clean = token.trim()
    require(clean.length in 16..4096) { "Push token ไม่ถูกต้อง" }
    http.function(
        "queuego-push",
        auth.session.accessToken,
        JSONObject()
            .put("action", "subscribe-native")
            .put("deviceId", pushDeviceId)
            .put("platform", "android")
            .put("token", clean)
    )
}

suspend fun disableNativePush(
    auth: NativeAuth,
    store: SecureRoleSessionStore,
    http: QueueGoNativeApi = QueueGoNativeApi()
) {
    val id = store.pushDeviceId()
    runCatching {
        http.function(
            "queuego-push",
            auth.session.accessToken,
            JSONObject()
                .put("action", "unsubscribe-native")
                .put("deviceId", id)
        )
    }
}

suspend fun syncNativePush(
    context: Context,
    auth: NativeAuth,
    expectedRole: String,
    store: SecureRoleSessionStore,
    channelName: String,
    channelDescription: String,
    highImportance: Boolean = false,
    http: QueueGoNativeApi = QueueGoNativeApi()
): Boolean {
    if (auth.user.status != "active" || auth.user.role != expectedRole) {
        disableNativePush(auth, store, http)
        return false
    }
    ensureNativePushChannel(context, channelName, channelDescription, highImportance)
    if (!nativeFirebaseConfigured(context)) return false
    val token = nativeFirebaseToken().trim()
    require(token.length >= 16) { "ไม่ได้รับ Push token จาก Firebase" }
    subscribeNativePush(auth, store.pushDeviceId(), token, http)
    return true
}

suspend fun testNativePush(
    auth: NativeAuth,
    http: QueueGoNativeApi = QueueGoNativeApi()
) {
    http.function(
        "queuego-push",
        auth.session.accessToken,
        JSONObject().put("action", "test")
    )
}

fun showNativePush(
    context: Context,
    title: String?,
    body: String?,
    data: Map<String, String>,
    smallIcon: Int,
    fallbackTitle: String
) {
    if (!nativePushPermissionGranted(context)) return
    ensureNativePushChannel(
        context,
        if (fallbackTitle.contains("Merchant", ignoreCase = true)) "QueueGo Merchant" else fallbackTitle,
        "แจ้งเตือนออเดอร์ ข้อความ และสถานะจาก QueueGo",
        highImportance = fallbackTitle.contains("Merchant", ignoreCase = true)
    )

    val referenceId = data["referenceId"] ?: data["reference_id"].orEmpty()
    val type = data["type"].orEmpty()
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("queuego_push_reference_id", referenceId)
        putExtra("queuego_push_type", type)
        putExtra("queuego_push_notification_id", data["notificationId"].orEmpty())
    } ?: return
    val pendingIntent = PendingIntent.getActivity(
        context,
        (referenceId.ifBlank { data["notificationId"].orEmpty() }).hashCode(),
        launch,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val cleanTitle = title?.takeIf { it.isNotBlank() } ?: fallbackTitle
    val cleanBody = body?.takeIf { it.isNotBlank() } ?: "มีรายการอัปเดต กรุณาเปิด QueueGo"
    val notification = NotificationCompat.Builder(context, QUEUEGO_NATIVE_PUSH_CHANNEL)
        .setSmallIcon(smallIcon)
        .setContentTitle(cleanTitle.take(80))
        .setContentText(cleanBody.take(220))
        .setStyle(NotificationCompat.BigTextStyle().bigText(cleanBody.take(220)))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(
        (data["notificationId"] ?: referenceId.ifBlank { System.currentTimeMillis().toString() }).hashCode(),
        notification
    )
}

/**
 * Shared Customer/Merchant FCM service. These two apps use SecureRoleSessionStore and
 * NativeAuth, so token rotation, validation and notification rendering stay one implementation.
 */
abstract class QueueGoNativeMessagingService : FirebaseMessagingService() {
    protected abstract val expectedRole: String
    protected abstract val roleStoreKey: String
    protected abstract val notificationIcon: Int
    protected abstract val notificationLabel: String
    protected open val highImportance: Boolean = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.length < 16) return
        val store = SecureRoleSessionStore(applicationContext, roleStoreKey)
        val cached = store.load() ?: return
        serviceScope.launch {
            runCatching {
                val api = NativeAuthApi()
                val validated = api.validate(cached, expectedRole) { store.save(it) }
                store.save(validated)
                if (validated.user.status == "active" && validated.user.role == expectedRole) {
                    subscribeNativePush(validated, store.pushDeviceId(), token)
                } else {
                    disableNativePush(validated, store)
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val cached = SecureRoleSessionStore(applicationContext, roleStoreKey).load() ?: return
        if (cached.user.status != "active" || cached.user.role != expectedRole) return
        showNativePush(
            this,
            message.notification?.title ?: message.data["title"],
            message.notification?.body ?: message.data["message"],
            message.data,
            notificationIcon,
            notificationLabel
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
