package com.queuego.rider

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max

class RiderReturnService : Service() {
    companion object {
        private const val CHANNEL_ID = "queuego_rider_google_maps_return"
        private const val NOTIFICATION_ID = 4107
        private const val POSITION_PREFS = "queuego_rider_return_overlay"
        private const val PREF_EDGE = "edge"
        private const val PREF_Y_RATIO = "y_ratio"
        private const val PREF_HINT_SHOWN = "hint_shown"

        fun start(context: Context) {
            // Overlay/navigation return is a work-only capability. Refuse to start
            // from stale UI state for pending, suspended, or inactive Riders.
            val cached = SessionStore(context.applicationContext).load() ?: return
            if (cached.user.status != "active") return
            val intent = Intent(context, RiderReturnService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w("QueueGoReturnService", "foreground_start_rejected", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RiderReturnService::class.java))
        }
    }

    private var overlay: View? = null
    private lateinit var windowManager: WindowManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-check at the service boundary as Android may restart/dispatch a service
        // after the screen that originally launched it has gone away.
        val cached = SessionStore(applicationContext).load()
        if (cached?.user?.status != "active") {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.qg_notification)
            .setContentTitle("QueueGo Rider")
            .setContentText("แตะเพื่อกลับไปยังงานที่กำลังทำ")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(returnPendingIntent())
            .build()

        val foregroundStarted = runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            Log.w("QueueGoReturnService", "start_foreground_failed", it)
        }.isSuccess

        if (!foregroundStarted) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        if (Settings.canDrawOverlays(this)) showOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (overlay != null) return

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density + .5f).toInt()

        val prefs = getSharedPreferences(POSITION_PREFS, Context.MODE_PRIVATE)
        val showHint = !prefs.getBoolean(PREF_HINT_SHOWN, false)
        val collapsedSize = dp(56)
        val expandedWidth = dp(146)
        val margin = dp(12)
        val topSafe = dp(54)
        val bottomSafe = dp(92)

        val bubble = TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.rgb(230, 0, 45))
            elevation = 12f * density
            setPadding(dp(12), 0, dp(12), 0)
            background = premiumOverlayBackground(dp(2))
            val brand = requireNotNull(getDrawable(R.drawable.qg_rider_brand_q))
            brand.setBounds(0, 0, dp(40), dp(40))
            setCompoundDrawables(brand, null, null, null)
            compoundDrawablePadding = if (showHint) dp(6) else 0
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            text = if (showHint) "กลับ QueueGo" else ""
            contentDescription = "กลับ QueueGo"
        }

        val params = WindowManager.LayoutParams(
            if (showHint) expandedWidth else collapsedSize,
            collapsedSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        fun screenWidth(): Int = resources.displayMetrics.widthPixels
        fun screenHeight(): Int = resources.displayMetrics.heightPixels
        fun clampY(value: Int): Int {
            val maxY = max(topSafe, screenHeight() - params.height - bottomSafe)
            return value.coerceIn(topSafe, maxY)
        }
        fun edgeX(edge: Int): Int =
            if (edge == 1) max(margin, screenWidth() - params.width - margin) else margin
        fun persistedY(): Int {
            val maxY = max(topSafe, screenHeight() - params.height - bottomSafe)
            val ratio = prefs.getFloat(PREF_Y_RATIO, .34f).coerceIn(0f, 1f)
            return clampY((topSafe + (maxY - topSafe) * ratio).toInt())
        }
        fun persist(edge: Int) {
            val maxY = max(topSafe, screenHeight() - params.height - bottomSafe)
            val span = max(1, maxY - topSafe)
            val ratio = ((params.y - topSafe).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            prefs.edit().putInt(PREF_EDGE, edge).putFloat(PREF_Y_RATIO, ratio).apply()
        }
        fun snapToNearestEdge(persistPosition: Boolean) {
            val edge = if (params.x + params.width / 2 >= screenWidth() / 2) 1 else 0
            params.x = edgeX(edge)
            params.y = clampY(params.y)
            runCatching { windowManager.updateViewLayout(bubble, params) }
            if (persistPosition) persist(edge)
        }

        val storedEdge = prefs.getInt(PREF_EDGE, 0).coerceIn(0, 1)
        params.x = edgeX(storedEdge)
        params.y = persistedY()

        var downX = 0
        var downY = 0
        var touchX = 0f
        var touchY = 0f

        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bubble.alpha = .82f
                    bubble.scaleX = .96f
                    bubble.scaleY = .96f
                    downX = params.x
                    downY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (downX + (event.rawX - touchX).toInt())
                        .coerceIn(0, max(0, screenWidth() - params.width))
                    params.y = clampY(downY + (event.rawY - touchY).toInt())
                    runCatching { windowManager.updateViewLayout(bubble, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - touchX) + abs(event.rawY - touchY)
                    bubble.alpha = 1f
                    bubble.scaleX = 1f
                    bubble.scaleY = 1f
                    snapToNearestEdge(persistPosition = true)
                    if (moved < 18f * density) returnToQueueGo()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    bubble.alpha = 1f
                    bubble.scaleX = 1f
                    bubble.scaleY = 1f
                    snapToNearestEdge(persistPosition = true)
                    true
                }
                else -> false
            }
        }

        runCatching {
            windowManager.addView(bubble, params)
            overlay = bubble
        }.onSuccess {
            if (showHint) {
                bubble.postDelayed({
                    if (overlay !== bubble) return@postDelayed
                    val edge = if (params.x + params.width / 2 >= screenWidth() / 2) 1 else 0
                    params.width = collapsedSize
                    params.x = edgeX(edge)
                    params.y = clampY(params.y)
                    bubble.setPadding(0, 0, 0, 0)
                    bubble.setPadding(dp(8), 0, dp(8), 0)
                    bubble.compoundDrawablePadding = 0
                    bubble.text = ""
                    bubble.textSize = 13f
                    bubble.typeface = Typeface.DEFAULT_BOLD
                    runCatching { windowManager.updateViewLayout(bubble, params) }
                    prefs.edit().putBoolean(PREF_HINT_SHOWN, true).apply()
                    persist(edge)
                }, 2400L)
            }
        }.onFailure {
            Log.w("QueueGoReturnService", "overlay_add_failed", it)
        }
    }

    private fun premiumOverlayBackground(strokeWidth: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.WHITE, Color.rgb(255, 247, 249))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setStroke(strokeWidth, Color.WHITE)
        }

    private fun returnToQueueGo() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
            )
        }.onFailure {
            Log.w("QueueGoReturnService", "overlay_return_failed", it)
        }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        runCatching { windowManager.removeView(view) }
        overlay = null
    }

    private fun returnPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "กลับ QueueGo จาก Google Maps",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ปุ่มลอยและทางลัดกลับสู่งาน QueueGo ระหว่างใช้ Google Maps"
            }
        )
    }
}
