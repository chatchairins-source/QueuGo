package com.queuego.shared

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Records only coarse Android process-exit metadata after the next app launch.
 * No stack trace, order/customer data, token, location or user-entered text is read.
 */
object NativeExitDiagnostics {
    private const val TAG = "QueueGoExit"

    fun recordPreviousProcessExit(context: Context, role: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("queuego_exit_diagnostics", Context.MODE_PRIVATE)
            val lastSeen = prefs.getLong("last_exit_timestamp_ms", 0L)
            val manager = app.getSystemService(ActivityManager::class.java)
            val exits = manager.getHistoricalProcessExitReasons(app.packageName, 0, 5)
                .filter { it.timestamp > lastSeen }
                .sortedBy { it.timestamp }
            exits.forEach { info ->
                Log.w(
                    TAG,
                    "role=" + role +
                        " reason=" + reasonLabel(info.reason) +
                        " reasonCode=" + info.reason +
                        " status=" + info.status +
                        " importance=" + info.importance +
                        " pssKb=" + info.pss +
                        " rssKb=" + info.rss +
                        " timestampMs=" + info.timestamp
                )
            }
            exits.maxOfOrNull { it.timestamp }?.let { newest ->
                prefs.edit().putLong("last_exit_timestamp_ms", newest).apply()
            }
        }.onFailure {
            Log.w(TAG, "exit_diagnostics_unavailable role=" + role)
        }
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
        ApplicationExitInfo.REASON_FREEZER -> "freezer"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package_state_change"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package_updated"
        else -> "other_or_unknown"
    }
}
