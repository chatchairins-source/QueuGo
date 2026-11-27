package com.queuego.shared

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/** Keeps the existing WebRTC peer eligible for background microphone use.
 * This service never creates media, authenticates users or joins a call itself.
 */
class NativeVoiceForegroundService : Service() {
    private var serviceOwner: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val owner = intent?.getStringExtra(EXTRA_OWNER)
        val current = synchronized(lock) { lease?.takeIf { it.owner == owner } }
        if (current == null) {
            if (synchronized(lock) { lease == null }) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            release(current.owner, null)
            stopSelf()
            return START_NOT_STICKY
        }
        serviceOwner = current.owner
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "สายโทร QueueGo", NotificationManager.IMPORTANCE_LOW))
            val open = requireNotNull(packageManager.getLaunchIntentForPackage(packageName))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val returnToCall = PendingIntent.getActivity(this, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, NOTIFICATION_ID,
                Intent(this, NativeVoiceForegroundService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_OWNER, current.owner),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.qg_voice_notification)
                .setContentTitle("โทรผ่าน QueueGo")
                .setContentText("แตะเพื่อกลับไปที่สายโทร")
                .setContentIntent(returnToCall)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "วางสาย", stop).build())
                .build()
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            current.ready.complete(Unit)
        } catch (failure: Exception) {
            release(current.owner, failure)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceOwner?.let { release(it, null) }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceOwner?.let { release(it, IllegalStateException("ระบบหยุดการคงสายโทร")) }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "queuego_active_voice"
        private const val NOTIFICATION_ID = 71041
        private const val EXTRA_OWNER = "voice_owner"
        private const val ACTION_STOP = "com.queuego.voice.STOP"
        private val lock = Any()
        private data class Lease(val owner: String, val ready: CompletableDeferred<Unit>, val stopped: (Throwable?) -> Unit)
        private var lease: Lease? = null

        fun prepare(context: Context, owner: String, onStopped: (Throwable?) -> Unit): Deferred<Unit> = synchronized(lock) {
            lease?.let {
                check(it.owner == owner) { "มีสายโทรอื่นกำลังใช้งาน" }
                return@synchronized it.ready
            }
            check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                "กรุณาอนุญาตไมโครโฟนก่อนโทร"
            }
            val process = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(process)
            check(process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                "กรุณากลับเข้า QueueGo เพื่อเริ่มหรือรับสาย"
            }
            val next = Lease(owner, CompletableDeferred(), onStopped)
            lease = next
            try {
                context.startForegroundService(Intent(context, NativeVoiceForegroundService::class.java).putExtra(EXTRA_OWNER, owner))
            } catch (failure: Exception) {
                lease = null
                next.ready.completeExceptionally(failure)
                throw failure
            }
            next.ready
        }

        fun stop(context: Context, owner: String) = synchronized(lock) {
            if (lease?.owner != owner) return@synchronized
            val previous = lease
            lease = null
            previous?.ready?.cancel()
            context.stopService(Intent(context, NativeVoiceForegroundService::class.java))
        }

        fun isReady(owner: String): Boolean = synchronized(lock) {
            lease?.let { it.owner == owner && it.ready.isCompleted && !it.ready.isCancelled } == true
        }

        private fun release(owner: String, failure: Throwable?) {
            val current = synchronized(lock) {
                lease?.takeIf { it.owner == owner }?.also { lease = null }
            } ?: return
            current.ready.completeExceptionally(failure ?: IllegalStateException("สายโทรสิ้นสุดแล้ว"))
            current.stopped(failure)
        }
    }
}
