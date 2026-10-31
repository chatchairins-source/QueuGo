package com.queuego.customer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgLine
import com.queuego.shared.QgLocationPickerMap
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgPlaceSearchPanel
import com.queuego.shared.QueueGoPlaceSearchResult
import com.queuego.shared.queueGoPlaceLabel
import kotlinx.coroutines.delay

@Composable
internal fun CustomerLocationPickerScreen(
    location: CustomerLocation?,
    address: String,
    busy: Boolean,
    searchPlaces: suspend (String) -> List<QueueGoPlaceSearchResult>,
    resolveAddress: suspend (Double, Double) -> String?,
    onSave: (CustomerLocation) -> Unit,
    onBack: () -> Unit,
    readGps: (suspend () -> CustomerLocation?)? = null,
    rideStop: String? = null,
    cameraReference: CustomerLocation? = null
) {
    val draftSaver = remember {
        listSaver<CustomerLocationDraft, Any>(
            save = { listOf(it.point != null, it.point?.latitude ?: 0.0,
                it.point?.longitude ?: 0.0, it.address, it.manualAddress, it.revision) },
            restore = { CustomerLocationDraft(
                if (it[0] as Boolean) CustomerLocation(it[1] as Double, it[2] as Double, it[3] as String) else null,
                it[3] as String, it[4] as Boolean, it[5] as Long) }
        )
    }
    var draft by rememberSaveable(stateSaver = draftSaver) {
        mutableStateOf(CustomerLocationDraft(
            location?.takeIf { validCustomerPoint(it.latitude, it.longitude) }, address))
    }
    val selected = draft.point
    val draftAddress = draft.address
    var recenterToken by remember { mutableIntStateOf(0) }
    var mapReady by remember { mutableStateOf(false) }
    var mapFailed by remember { mutableStateOf(false) }
    var mapAttempt by remember { mutableIntStateOf(0) }
    var autoGpsRequested by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val mapHeight = if (rideStop != null) 245.dp
        else (LocalConfiguration.current.screenHeightDp.dp * 0.34f).coerceIn(240.dp, 400.dp)
    val mapCorner = if (rideStop != null) 16.dp else 20.dp
    val ridePickup = rideStop == "pickup"
    val rideDestination = rideStop == "destination"
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var gpsBusy by remember { mutableStateOf(false) }
    var permissionPending by remember { mutableStateOf(false) }
    var gpsMessage by remember { mutableStateOf<String?>(null) }

    fun loadGps() {
        if (gpsBusy) return
        gpsBusy = true
        scope.launch {
            try {
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                val point = if (readGps != null) readGps() else {
                    val cached = lastKnownLocation(context)?.takeIf { validCustomerPoint(it.first, it.second) }
                    val position = if (cached == null) customerRegistrationLocation(context) else null
                    cached?.let { CustomerLocation(it.first, it.second, "") }
                        ?: position?.let { CustomerLocation(it.latitude, it.longitude, "") }
                }
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                if (point == null || !validCustomerPoint(point.latitude, point.longitude)) {
                    gpsMessage = "ยังอ่านตำแหน่ง GPS ไม่ได้ เลือกจุดบนแผนที่ได้"
                } else {
                    draft = draft.move(point)
                    recenterToken++
                    gpsMessage = null
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { gpsMessage = "อ่านตำแหน่งไม่สำเร็จ กรุณาเปิด GPS หรือเลือกจุดบนแผนที่" }
            finally { gpsBusy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionPending = false
        if (grants.values.any { it }) loadGps()
        else gpsMessage = "เลือกจุดบนแผนที่ได้โดยไม่ต้องใช้ GPS"
    }
    fun requestGps() {
        if (gpsBusy || permissionPending) return
        if (readGps != null || hasLocation(context)) loadGps()
        else {
            permissionPending = true
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(mapReady, rideStop, location) {
        // Web Ride only auto-locates an empty pickup. A destination must stay
        // unselected even when the pickup is used to center its viewport.
        if (rideDestination || ridePickup && location != null) return@LaunchedEffect
        // Do not launch the Android location-permission dialog while QueueGo map surface
        // is still being created. On physical devices that Activity pause can race
        // map renderer initialization and close the Customer app.
        // Keep Production behavior (automatic GPS on open) but only after the map is ready.
        if (!mapReady || autoGpsRequested) return@LaunchedEffect
        autoGpsRequested = true
        delay(150)
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        requestGps()
    }

    LaunchedEffect(mapReady, mapAttempt) {
        if (mapReady) {
            mapFailed = false
            return@LaunchedEffect
        }
        delay(12_000)
        if (!mapReady) mapFailed = true
    }

    LaunchedEffect(selected?.latitude, selected?.longitude, draft.revision) {
        val point = selected ?: return@LaunchedEffect
        if (draft.manualAddress || draft.address.isNotBlank()) return@LaunchedEffect
        val ticket = draft.revision
        delay(350)
        val resolved = try { resolveAddress(point.latitude, point.longitude) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { null }
        draft = draft.resolved(ticket, resolved)
    }

    val selectedPoint = selected?.let { QgMapPoint(it.latitude, it.longitude, 0) }
    val externalPoint = selectedPoint

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFECEEF2), RoundedCornerShape(13.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.qg_shop_back),
                    contentDescription = "ย้อนกลับ",
                    tint = Color(0xFF24272D),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    when (rideStop) {
                        "pickup" -> "เลือกจุดรับ"
                        "destination" -> "เลือกจุดหมาย"
                        else -> "เลือกที่อยู่จัดส่ง"
                    },
                    color = Color(0xFF17191D),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp
                )
                Text(
                    when (rideStop) {
                        "pickup" -> "แตะตำแหน่งบนแผนที่เพื่อปักจุดรับของคุณ"
                        "destination" -> "แตะตำแหน่งบนแผนที่เพื่อปักจุดส่งของคุณ"
                        else -> "เลือกตำแหน่งบน QueueGo Map หรือใช้ GPS ปัจจุบัน"
                    },
                    color = QgMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (rideStop == null) {
            QgPlaceSearchPanel(
                enabled = !busy,
                search = searchPlaces,
                onSelect = { result ->
                    draft = draft.move(
                        CustomerLocation(
                            result.latitude,
                            result.longitude,
                            queueGoPlaceLabel(result)
                        )
                    )
                    recenterToken++
                }
            )
            Spacer(Modifier.height(10.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(mapHeight)
                .semantics { stateDescription = if (mapReady) "แผนที่พร้อมใช้งาน" else "กำลังโหลดแผนที่" }
                .clip(RoundedCornerShape(mapCorner))
                .background(Color(0xFFF0F1F2))
                .border(1.dp, QgLine, RoundedCornerShape(mapCorner))
        ) {
            key(mapAttempt) {
                QgLocationPickerMap(
                    initialPoint = selectedPoint,
                    recenterPoint = externalPoint,
                    recenterToken = recenterToken,
                    initialCameraPoint = if (rideDestination && selectedPoint == null)
                        cameraReference?.let { QgMapPoint(it.latitude, it.longitude, 0) } else null,
                    modifier = Modifier.fillMaxSize(),
                    onPointSelected = { point ->
                        draft = draft.move(CustomerLocation(point.latitude, point.longitude, ""))
                    },
                    onReady = { mapReady = it; if (it) mapFailed = false }
                )
            }
            if (!mapReady && mapFailed) {
                Column(
                    Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("โหลด QueueGo Map ไม่สำเร็จ ตรวจอินเทอร์เน็ตแล้วลองใหม่", color = QgMuted)
                    TextButton(onClick = { mapAttempt++; mapReady = false; mapFailed = false }) {
                        Text("ลองโหลดแผนที่อีกครั้ง")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (rideStop != null) 67.dp else 66.dp)
                .clip(RoundedCornerShape(if (rideStop != null) 14.dp else 16.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE6E7EA), RoundedCornerShape(if (rideStop != null) 14.dp else 16.dp))
                .clickable(enabled = !gpsBusy && !busy, onClick = ::requestGps)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(if (rideStop != null) 32.dp else 42.dp)
                    .clip(RoundedCornerShape(if (rideStop != null) 16.dp else 21.dp))
                    .background(Color(0xFFFFF0F2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.qg_home_pin),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = QgRed
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (gpsBusy) "กำลังอ่านตำแหน่ง GPS…" else "ใช้ตำแหน่ง GPS ปัจจุบัน",
                    color = Color(0xFF17191D),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
                Text(
                    "ค้นหาตำแหน่งของคุณบนแผนที่",
                    color = QgMuted,
                    fontSize = 11.sp
                )
            }
            Text("›", color = Color(0xFF9AA0A8), fontSize = if (rideStop != null) 22.sp else 28.sp)
        }
        gpsMessage?.let {
            Text(
                it,
                color = QgMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            when (rideStop) {
                "pickup" -> "ที่อยู่จุดรับ"
                "destination" -> "ที่อยู่จุดส่ง"
                else -> "ที่อยู่จัดส่ง"
            },
            color = Color(0xFF777E88),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = draftAddress,
            onValueChange = {
                draft = draft.edit(it)
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text(
                if (rideStop != null) "ที่อยู่หรือจุดสังเกต"
                else "บ้านเลขที่ หมู่บ้าน ซอย ถนน หรือจุดสังเกต",
                color = QgMuted
            ) },
            shape = RoundedCornerShape(if (rideStop != null) 14.dp else 15.dp)
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                selected?.let { onSave(it.copy(address = draftAddress.trim())) }
            },
            enabled = !busy && selected != null &&
                (if (rideStop != null) draftAddress.trim().length >= 3 else draftAddress.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = QgRed)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text(if (rideStop != null) "ใช้จุดนี้" else "บันทึกที่อยู่นี้",
                fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(26.dp))
    }
}

private fun validCustomerPoint(latitude: Double, longitude: Double): Boolean =
    latitude in 5.0..21.0 && longitude in 97.0..106.0 && !(latitude == 0.0 && longitude == 0.0)
