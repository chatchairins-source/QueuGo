package com.queuego.customer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
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
import java.util.Locale

internal const val CUSTOMER_NOTIFICATION_CHANNEL = "queuego_orders_v2"
private const val CUSTOMER_PUSH_PREFS = "queuego_customer_push_preferences"
private const val CUSTOMER_PUSH_ENABLED = "push_enabled"
private const val CUSTOMER_PROFILE_PREFS = "qg_customer_profile_preferences"
private const val CUSTOMER_ARRIVAL_SOUND = "arrival_sound"

internal fun customerArrivalSoundEnabled(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(CUSTOMER_PROFILE_PREFS, Context.MODE_PRIVATE)
        .getBoolean(CUSTOMER_ARRIVAL_SOUND, false)

private fun announceCustomerArrival(context: Context, text: String) {
    var speaker: TextToSpeech? = null
    speaker = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            speaker?.language = Locale("th", "TH")
            speaker?.speak(
                text.take(120),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "queuego-rider-arrival"
            )
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { speaker?.stop() }
                runCatching { speaker?.shutdown() }
            }, 6_000L)
        } else {
            runCatching { speaker?.shutdown() }
        }
    }
}

internal fun customerPushEnabled(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(CUSTOMER_PUSH_PREFS, Context.MODE_PRIVATE)
        .getBoolean(CUSTOMER_PUSH_ENABLED, true)

internal fun setCustomerPushEnabled(context: Context, enabled: Boolean) {
    context.applicationContext
        .getSharedPreferences(CUSTOMER_PUSH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(CUSTOMER_PUSH_ENABLED, enabled)
        .apply()
}

internal fun ensureCustomerNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
    manager.createNotificationChannel(
        NotificationChannel(
            CUSTOMER_NOTIFICATION_CHANNEL,
            "การแจ้งเตือน QueueGo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "ออเดอร์ สถานะการจัดส่ง แชต และสายเรียกเข้า QueueGo"
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 280L, 140L, 280L)
            setShowBadge(true)
            setSound(sound, attributes)
        }
    )
}

internal fun customerFirebaseConfigured(context: Context): Boolean =
    runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

private suspend fun customerFirebaseToken(): String = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token -> if (continuation.isActive) continuation.resume(token) }
        .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
}

internal suspend fun syncCustomerNativePush(context: Context, auth: NativeAuth): Boolean {
    if (!customerPushEnabled(context)) return false
    val store = NativePushDeviceStore(context, "customer")
    val api = NativePushApi()
    if (auth.user.role != "customer" || auth.user.status != "active") {
        runCatching { api.unsubscribe(auth, store.deviceId()) }
        return false
    }
    ensureCustomerNotificationChannel(context)
    if (!customerFirebaseConfigured(context)) return false
    val token = customerFirebaseToken().trim()
    require(token.length >= 16) { "ไม่ได้รับ Push token จาก Firebase" }
    api.subscribe(auth, store.deviceId(), token)
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
        if (token.length < 16) return
        if (!customerPushEnabled(applicationContext)) return
        val sessionStore = SecureRoleSessionStore(applicationContext, "customer")
        val cached = sessionStore.load() ?: return
        serviceScope.launch {
            runCatching {
                val auth = NativeAuthApi().validate(cached, "customer") { sessionStore.save(it) }
                if (auth.user.status == "active") {
                    NativePushApi().subscribe(
                        auth,
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
        if (cached.user.role != "customer" || cached.user.status != "active") return
        ensureCustomerNotificationChannel(this)
        val title = message.notification?.title ?: message.data["title"] ?: "QueueGo"
        val body = message.notification?.body ?: message.data["message"] ?: "มีรายการอัปเดตใน QueueGo"
        val referenceId = message.data["referenceId"] ?: message.data["reference_id"] ?: ""
        val type = message.data["type"].orEmpty()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("queuego_push_reference_id", referenceId)
            putExtra("queuego_push_type", type)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (message.messageId ?: referenceId + type).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CUSTOMER_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.qg_notification)
            .setContentTitle(title.take(80))
            .setContentText(body.take(220))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(220)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0L, 280L, 140L, 280L))
            .setCategory(
                if (type == "voice_call") NotificationCompat.CATEGORY_CALL
                else NotificationCompat.CATEGORY_MESSAGE
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            (message.messageId ?: referenceId.ifBlank { System.currentTimeMillis().toString() }).hashCode(),
            notification
        )
        val arrivalEvent = type.lowercase() in setOf(
            "rider_arrived",
            "rider_arrived_customer",
            "arrival"
        ) || Regex("มาถึง|ถึงจุดส่ง|ถึงลูกค้า").containsMatchIn(title + " " + body)
        if (arrivalEvent && customerArrivalSoundEnabled(this)) {
            announceCustomerArrival(this, title.ifBlank { "Rider มาถึงแล้ว" })
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
