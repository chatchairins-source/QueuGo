package com.queuego.customer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgIcon
import com.queuego.shared.QgLine
import com.queuego.shared.QgLongdoLocationPickerMap
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.delay

@Composable
internal fun CustomerLocationPickerScreen(
    location: CustomerLocation?,
    address: String,
    busy: Boolean,
    resolveAddress: suspend (Double, Double) -> String?,
    onSave: (CustomerLocation) -> Unit,
    onBack: () -> Unit,
    readGps: (suspend () -> CustomerLocation?)? = null,
    title: String = "เลือกที่อยู่จัดส่ง",
    subtitle: String = "ปักหมุดตำแหน่งรับสินค้า แล้วระบุรายละเอียดที่อยู่ด้านล่าง",
    saveLabel: String = "บันทึกที่อยู่นี้",
    autoUseGps: Boolean = true,
    mapHeight: Dp = 150.dp,
    cameraLocation: CustomerLocation? = null
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
    var autoGpsRequested by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
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

    LaunchedEffect(mapReady) {
        // Do not launch the Android location-permission dialog while Longdo's GL surface
        // is still being created. On physical devices that Activity pause can race
        // Longdo 0.1.8 renderer initialization and close the Customer app.
        // Keep Production behavior (automatic GPS on open) but only after the map is ready.
        if (!autoUseGps || !mapReady || autoGpsRequested) return@LaunchedEffect
        autoGpsRequested = true
        delay(150)
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        requestGps()
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
    val cameraPoint = cameraLocation
        ?.takeIf { validCustomerPoint(it.latitude, it.longitude) }
        ?.let { QgMapPoint(it.latitude, it.longitude, 0) }
        ?: selectedPoint
    val externalPoint = selectedPoint
    var observedCenter by remember(cameraPoint?.latitude, cameraPoint?.longitude) {
        mutableStateOf(cameraPoint)
    }

    val addressLabel = when {
        title.contains("จุดรับ") -> "ที่อยู่จุดรับ"
        title.contains("จุดหมาย") -> "ที่อยู่จุดส่ง"
        else -> "ที่อยู่จัดส่ง"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF111827)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = Color(0xFF8A9099),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(18.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFFECEEF2), RoundedCornerShape(24.dp))
                .padding(14.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .semantics { stateDescription = if (mapReady) "แผนที่พร้อมใช้งาน" else "กำลังโหลดแผนที่" }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF0F1F2))
                    .border(1.dp, Color(0xFFE7E9ED), RoundedCornerShape(20.dp))
            ) {
                QgLongdoLocationPickerMap(
                    initialPoint = cameraPoint,
                    recenterPoint = externalPoint,
                    recenterToken = recenterToken,
                    modifier = Modifier.fillMaxSize(),
                    selectInitialPoint = selectedPoint != null,
                    onCenterChanged = { point ->
                        observedCenter = point
                        draft = draft.move(CustomerLocation(point.latitude, point.longitude, ""))
                    },
                    onCenterObserved = { point -> observedCenter = point },
                    onReady = { mapReady = it }
                )
                if (!mapReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
                OutlinedButton(
                    onClick = {
                        observedCenter?.takeIf { it.valid }?.let { point ->
                            draft = draft.move(CustomerLocation(point.latitude, point.longitude, ""))
                        }
                    },
                    enabled = !busy && observedCenter?.valid == true,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).height(42.dp),
                    shape = RoundedCornerShape(99.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xF7FFFFFF),
                        contentColor = Color(0xFF343941)
                    )
                ) {
                    Text("เลือกตรงกลาง", fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE4E7EC), RoundedCornerShape(17.dp))
                    .clickable(enabled = !gpsBusy && !busy, onClick = ::requestGps)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(Color(0xFFFFF0F2), RoundedCornerShape(99.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    QgIcon("pin", Modifier.size(20.dp), QgRed)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (gpsBusy) "กำลังอ่านตำแหน่ง GPS…" else "ใช้ตำแหน่ง GPS ปัจจุบัน",
                        color = QgRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "ค้นหาตำแหน่งของคุณบนแผนที่",
                        color = Color(0xFF9AA0AA),
                        fontSize = 13.sp
                    )
                }
                Text("›", color = Color(0xFF6F7680), fontSize = 28.sp)
            }

            gpsMessage?.let {
                Spacer(Modifier.height(7.dp))
                Text(it, color = QgMuted, fontSize = 13.sp)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                addressLabel,
                color = Color(0xFF8B929D),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                value = draftAddress,
                onValueChange = { draft = draft.edit(it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(17.dp),
                placeholder = { Text("ที่อยู่หรือจุดสังเกต") }
            )

            Text(
                selected?.let { "เลือกพิกัดแล้ว" } ?: "เลื่อนแผนที่หรือใช้ GPS เพื่อเลือกพิกัด",
                color = Color(0xFF8A9099),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 7.dp, start = 2.dp)
            )

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    selected?.let { onSave(it.copy(address = draftAddress.trim())) }
                },
                enabled = !busy && selected != null && draftAddress.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QgRed,
                    contentColor = Color.White
                )
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    QgIcon("pin", Modifier.size(20.dp), Color.White)
                    Spacer(Modifier.size(7.dp))
                    Text(saveLabel, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFF4F5),
                    contentColor = QgRed
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.qg_shop_back),
                    contentDescription = null,
                    tint = QgRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(7.dp))
                Text("กลับ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

private fun validCustomerPoint(latitude: Double, longitude: Double): Boolean =
    latitude in 5.0..21.0 && longitude in 97.0..106.0 && !(latitude == 0.0 && longitude == 0.0)
