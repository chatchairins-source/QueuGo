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
import com.queuego.shared.QueueGoNativeApi
import com.queuego.shared.SecureRoleSessionStore
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

private const val MERCHANT_PUSH_CHANNEL = "queuego_orders"
private const val MERCHANT_PUSH_CHANNEL_NAME = "การแจ้งเตือน QueueGo Merchant"

internal class MerchantPushStore(context: Context) {
    private val prefs = context.getSharedPreferences("queuego_merchant_push", Context.MODE_PRIVATE)

    fun deviceId(): String {
        prefs.getString("device_id", null)?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", created).apply()
        return created
    }

    fun notificationPermissionAsked(): Boolean =
        prefs.getBoolean("notification_permission_asked", false)

    fun markNotificationPermissionAsked() {
        prefs.edit().putBoolean("notification_permission_asked", true).apply()
    }
}

internal fun ensureMerchantPushChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
        .build()
    val channel = NotificationChannel(
        MERCHANT_PUSH_CHANNEL,
        MERCHANT_PUSH_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "ออเดอร์ ข้อความ และสายเรียกเข้า QueueGo Merchant"
        enableVibration(true)
        setSound(sound, attributes)
    }
    manager.createNotificationChannel(channel)
}

internal fun merchantFirebaseConfigured(context: Context): Boolean =
    runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

private suspend fun merchantFirebaseToken(): String = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token ->
            if (continuation.isActive) continuation.resume(token)
        }
        .addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
}

internal class MerchantNativePushApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun subscribe(auth: NativeAuth, deviceId: String, token: String) {
        require(deviceId.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "รหัสอุปกรณ์แจ้งเตือนไม่ถูกต้อง" }
        require(token.length in 16..4096) { "Push token ไม่ถูกต้อง" }
        http.functionPost(
            "queuego-push",
            auth.session.accessToken,
            JSONObject()
                .put("action", "subscribe-native")
                .put("deviceId", deviceId)
                .put("platform", "android")
                .put("token", token)
        )
    }

    suspend fun unsubscribe(auth: NativeAuth, deviceId: String) {
        if (!deviceId.matches(Regex("^[0-9a-fA-F-]{36}$"))) return
        http.functionPost(
            "queuego-push",
            auth.session.accessToken,
            JSONObject()
                .put("action", "unsubscribe-native")
                .put("deviceId", deviceId)
        )
    }
}

internal suspend fun syncMerchantNativePush(
    context: Context,
    auth: NativeAuth,
    store: MerchantPushStore,
    api: MerchantNativePushApi = MerchantNativePushApi()
): Boolean {
    if (auth.user.role != "shop" || auth.user.status != "active") {
        runCatching { api.unsubscribe(auth, store.deviceId()) }
        return false
    }
    ensureMerchantPushChannel(context)
    if (!merchantFirebaseConfigured(context)) return false
    val token = merchantFirebaseToken().trim()
    require(token.length in 16..4096) { "ไม่ได้รับ Push token จาก Firebase" }
    api.subscribe(auth, store.deviceId(), token)
    return true
}

class QueueGoMerchantMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.length !in 16..4096) return
        val auth = SecureRoleSessionStore(applicationContext, "shop").load() ?: return
        if (auth.user.role != "shop" || auth.user.status != "active") return
        val store = MerchantPushStore(applicationContext)
        serviceScope.launch {
            runCatching { MerchantNativePushApi().subscribe(auth, store.deviceId(), token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val auth = SecureRoleSessionStore(applicationContext, "shop").load() ?: return
        if (auth.user.role != "shop" || auth.user.status != "active") return
        ensureMerchantPushChannel(this)

        val type = message.data["type"].orEmpty()
        val referenceId = message.data["referenceId"]
            ?: message.data["reference_id"]
            ?: ""
        val title = message.notification?.title
            ?: message.data["title"]
            ?: if (type == "voice_call") "สายเรียกเข้า QueueGo" else "QueueGo Merchant"
        val body = message.notification?.body
            ?: message.data["message"]
            ?: if (type == "voice_call") "แตะเพื่อเปิด QueueGo Merchant และรับสาย" else "มีรายการอัปเดต"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("queuego_push_reference_id", referenceId)
            putExtra("queuego_push_type", type)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (message.messageId ?: referenceId.ifBlank { type }).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, MERCHANT_PUSH_CHANNEL)
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

        if (type == "voice_call") builder.setTimeoutAfter(50_000L)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            (message.messageId ?: referenceId.ifBlank { System.currentTimeMillis().toString() }).hashCode(),
            builder.build()
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
