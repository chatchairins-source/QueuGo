package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class RiderRoadAlert(
    val id: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val note: String?,
    val distanceKm: Double,
    val publishedAt: String?,
    val expiresAt: String?
)

internal val riderPublicRoadAlertCategories = setOf(
    "road_closed",
    "bad_road",
    "unsafe_turn"
)

internal fun riderRoadAlertsFromRpc(raw: Any?): List<RiderRoadAlert> {
    val rows = when (raw) {
        is JSONArray -> raw
        is JSONObject -> JSONArray().put(raw)
        else -> JSONArray()
    }
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("alert_id").takeIf { it.isNotBlank() && it != "null" } ?: continue
            val category = row.optString("category").takeIf { it in riderPublicRoadAlertCategories } ?: continue
            val latitude = row.optDouble("latitude", Double.NaN)
            val longitude = row.optDouble("longitude", Double.NaN)
            val distance = row.optDouble("distance_km", Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite() || !distance.isFinite() || distance < 0.0) continue
            if (latitude !in 5.0..21.0 || longitude !in 97.0..106.0) continue
            add(
                RiderRoadAlert(
                    id = id,
                    category = category,
                    latitude = latitude,
                    longitude = longitude,
                    note = row.optString("note").takeIf { it.isNotBlank() && it != "null" },
                    distanceKm = distance,
                    publishedAt = row.optString("published_at").takeIf { it.isNotBlank() && it != "null" },
                    expiresAt = row.optString("expires_at").takeIf { it.isNotBlank() && it != "null" }
                )
            )
        }
    }.sortedBy { it.distanceKm }
}

internal fun riderRoadAlertDistanceLabel(distanceKm: Double): String =
    when {
        !distanceKm.isFinite() || distanceKm < 0.0 -> "—"
        distanceKm < 1.0 -> "${(distanceKm * 1000.0).toInt().coerceAtLeast(0)} ม."
        else -> String.format(Locale.US, "%.1f กม.", distanceKm)
    }

private class RiderRoadAlertsApi(
    private val http: QueueGoNativeApi = QueueGoNativeApi()
) {
    suspend fun nearby(
        auth: QueueGoAuth,
        location: RiderSafetyLocation,
        radiusKm: Double = 5.0
    ): List<RiderRoadAlert> {
        val raw = http.rpc(
            "qg_rider_nearby_road_alerts",
            auth.session.accessToken,
            JSONObject()
                .put("p_latitude", location.latitude)
                .put("p_longitude", location.longitude)
                .put("p_radius_km", radiusKm)
        )
        return riderRoadAlertsFromRpc(raw)
    }
}

@Composable
internal fun RiderNearbyRoadAlertsCard(
    auth: QueueGoAuth,
    location: RiderSafetyLocation?,
    modifier: Modifier = Modifier
) {
    val api = remember { RiderRoadAlertsApi() }
    var alerts by remember(auth.user.id) { mutableStateOf<List<RiderRoadAlert>>(emptyList()) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }
    var error by remember(auth.user.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(
        auth.user.id,
        auth.session.accessToken,
        location?.latitude,
        location?.longitude
    ) {
        if (location == null) {
            alerts = emptyList()
            loaded = true
            error = null
            return@LaunchedEffect
        }
        while (true) {
            try {
                alerts = api.nearby(auth, location)
                error = null
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                error = "โหลดแจ้งเตือนถนนไม่สำเร็จ"
            } finally {
                loaded = true
            }
            delay(60_000L)
        }
    }

    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEEE6E8))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("แจ้งเตือนถนนใกล้คุณ", fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(
                "แสดงเฉพาะรายงานที่ Admin ตรวจและเผยแพร่แล้ว ภายในรัศมี 5 กม.",
                color = QgMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
            Spacer(Modifier.height(8.dp))

            when {
                location == null -> Text(
                    "ยังไม่มีตำแหน่งที่ใหม่พอ กรุณาเปิด GPS เพื่อดูแจ้งเตือนใกล้ตัว",
                    color = QgMuted,
                    fontSize = 9.sp
                )
                !loaded -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                alerts.isEmpty() && error == null -> Text(
                    "ยังไม่มีแจ้งเตือนถนนที่ยืนยันแล้วใกล้ตำแหน่งนี้",
                    color = QgMuted,
                    fontSize = 9.sp
                )
                else -> {
                    alerts.take(3).forEachIndexed { index, alert ->
                        if (index > 0) HorizontalDivider(
                            color = Color(0xFFF0EAEC),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                riderMapFeedbackCategoryLabel(alert.category),
                                color = if (alert.category == "road_closed") QgRed else Color(0xFF6B4B00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                riderRoadAlertDistanceLabel(alert.distanceKm),
                                color = QgMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        alert.note?.let {
                            Text(
                                it,
                                fontSize = 9.sp,
                                lineHeight = 12.sp,
                                color = Color(0xFF4D474A),
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                    if (alerts.size > 3) {
                        Text(
                            "และอีก ${alerts.size - 3} จุด",
                            color = QgMuted,
                            fontSize = 8.5.sp,
                            modifier = Modifier.padding(top = 7.dp)
                        )
                    }
                }
            }

            error?.let {
                Text(it, color = QgRed, fontSize = 8.5.sp, modifier = Modifier.padding(top = 7.dp))
            }
        }
    }
}
