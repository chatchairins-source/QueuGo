package com.queuego.customer

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

private const val CUSTOMER_PUSH_CHANNEL = "queuego_orders"
private const val CUSTOMER_PUSH_CHANNEL_NAME = "การแจ้งเตือน QueueGo"

internal fun ensureCustomerPushChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
        NotificationChannel(
            CUSTOMER_PUSH_CHANNEL,
            CUSTOMER_PUSH_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "ออเดอร์ ข้อความ และสายเรียกเข้า QueueGo"
            enableVibration(true)
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .build()
            )
        }
    )
}

internal fun customerFirebaseConfigured(context: Context): Boolean =
    runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

private suspend fun customerFirebaseToken(): String = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token ->
            if (continuation.isActive) continuation.resume(token)
        }
        .addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
}

internal suspend fun syncCustomerNativePush(context: Context, auth: NativeAuth): Boolean {
    val push = NativePushApi()
    val devices = NativePushDeviceStore(context, "customer")
    if (auth.user.status != "active") {
        runCatching { push.unsubscribe(auth, devices.deviceId()) }
        return false
    }
    ensureCustomerPushChannel(context)
    if (!customerFirebaseConfigured(context)) return false
    val token = customerFirebaseToken().trim()
    require(token.length in 16..4096) { "ไม่ได้รับ Push token จาก Firebase" }
    push.subscribe(auth, devices.deviceId(), token)
    return true
}

internal suspend fun disableCustomerNativePush(context: Context, auth: NativeAuth) {
    runCatching {
        NativePushApi().unsubscribe(auth, NativePushDeviceStore(context, "customer").deviceId())
    }
}

class QueueGoCustomerMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.length !in 16..4096) return
        val store = SecureRoleSessionStore(applicationContext, "customer")
        val cached = store.load() ?: return
        serviceScope.launch {
            runCatching {
                val validated = NativeAuthApi().validate(cached, "customer") { store.save(it) }
                store.save(validated)
                if (validated.user.status == "active") {
                    NativePushApi().subscribe(
                        validated,
                        NativePushDeviceStore(applicationContext, "customer").deviceId(),
                        token
                    )
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val cached = SecureRoleSessionStore(applicationContext, "customer").load() ?: return
        if (cached.user.status != "active") return
        ensureCustomerPushChannel(this)

        val title = message.notification?.title ?: message.data["title"] ?: "QueueGo"
        val body = message.notification?.body ?: message.data["message"]
            ?: "มีรายการอัปเดต กรุณาเปิด QueueGo"
        val type = message.data["type"].orEmpty()
        val referenceId = message.data["referenceId"]
            ?: message.data["reference_id"]
            ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("queuego_push_reference_id", referenceId)
            putExtra("queuego_push_type", type)
        }
        val pending = PendingIntent.getActivity(
            this,
            (message.messageId ?: referenceId.ifBlank { type }).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CUSTOMER_PUSH_CHANNEL)
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
            .setContentIntent(pending)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            (message.messageId ?: referenceId.ifBlank { System.currentTimeMillis().toString() }).hashCode(),
            notification
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
