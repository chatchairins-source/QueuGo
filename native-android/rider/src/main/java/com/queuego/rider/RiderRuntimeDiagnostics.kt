package com.queuego.rider

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.queuego.ridernavigationmap.NavigationMapDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object RiderRuntimeDiagnosticsStore {
    private val mutableMapDiagnostics = MutableStateFlow<NavigationMapDiagnostics?>(null)
    val mapDiagnostics: StateFlow<NavigationMapDiagnostics?> = mutableMapDiagnostics.asStateFlow()

    fun publishMap(value: NavigationMapDiagnostics) {
        mutableMapDiagnostics.value = value
    }
}

internal data class RiderMapRuntimeHealth(
    val healthy: Boolean,
    val headline: String,
    val detail: String
)

internal fun riderMapRuntimeHealth(value: NavigationMapDiagnostics): RiderMapRuntimeHealth {
    val healthy =
        value.mapCreatedCount <= 1 &&
            value.mapReplacementCount == 0 &&
            value.snapshotBridgeFailureCount == 0
    val headline = if (healthy) {
        "Map reuse ปกติ"
    } else {
        "พบการสร้าง/แทนที่ map ที่ควรตรวจ"
    }
    val detail =
        "surface ${value.viewCreatedCount} · map ${value.mapCreatedCount}" +
            " · replace ${value.mapReplacementCount}" +
            " · render ${value.renderPassCount}" +
            " · route rebuild ${value.routeLayerRebuildCount}" +
            " · snapshot fail ${value.snapshotBridgeFailureCount}"
    return RiderMapRuntimeHealth(healthy, headline, detail)
}

internal data class RiderProcessExitDiagnostic(
    val label: String,
    val detail: String,
    val problematic: Boolean
)

internal fun riderExitReasonLabel(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_CRASH -> "แอปปิดจาก crash"
    ApplicationExitInfo.REASON_ANR -> "แอปไม่ตอบสนอง (ANR)"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "ระบบปิดแอปเพราะหน่วยความจำต่ำ"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "ระบบปิดจากการใช้ทรัพยากรสูง"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "เริ่ม process ไม่สำเร็จ"
    ApplicationExitInfo.REASON_SIGNALED -> "process ถูก signal ให้หยุด"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency ของ process หยุดทำงาน"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "process รีสตาร์ตหลังสิทธิ์เปลี่ยน"
    ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "แอปถูกอัปเดต"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "ผู้ใช้/ระบบร้องขอให้ปิดแอป"
    ApplicationExitInfo.REASON_USER_STOPPED -> "ผู้ใช้หยุดแอป"
    ApplicationExitInfo.REASON_OTHER -> "แอปถูกปิดด้วยเหตุผลอื่น"
    else -> "เหตุผลการปิด process #$reason"
}

internal fun riderExitReasonProblematic(reason: Int): Boolean = reason in setOf(
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_ANR,
    ApplicationExitInfo.REASON_LOW_MEMORY,
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
    ApplicationExitInfo.REASON_SIGNALED,
    ApplicationExitInfo.REASON_DEPENDENCY_DIED
)

internal fun readRiderLastProcessExit(context: Context): RiderProcessExitDiagnostic? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val manager = context.getSystemService(ActivityManager::class.java) ?: return null
    val exit = runCatching {
        manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
    }.getOrNull() ?: return null
    return RiderProcessExitDiagnostic(
        label = riderExitReasonLabel(exit.reason),
        detail = "Android exit reason · importance " + exit.importance,
        problematic = riderExitReasonProblematic(exit.reason)
    )
}
