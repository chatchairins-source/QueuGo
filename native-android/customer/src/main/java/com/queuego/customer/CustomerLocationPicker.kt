package com.queuego.customer

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgLine
import com.queuego.shared.QgLongdoLocationPickerMap
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMuted
import kotlin.math.abs

@Composable
internal fun CustomerLocationPickerScreen(
    location: CustomerLocation?,
    address: String,
    busy: Boolean,
    onGps: () -> Unit,
    onSave: (CustomerLocation) -> Unit,
    onBack: () -> Unit
) {
    var selected by remember {
        mutableStateOf(location?.takeIf { validCustomerPoint(it.latitude, it.longitude) })
    }
    var draftAddress by remember(address) { mutableStateOf(address) }
    var recenterToken by remember { mutableIntStateOf(0) }
    var mapReady by remember { mutableStateOf(false) }

    LaunchedEffect(location?.latitude, location?.longitude) {
        val next = location?.takeIf { validCustomerPoint(it.latitude, it.longitude) } ?: return@LaunchedEffect
        val current = selected
        if (current == null ||
            abs(current.latitude - next.latitude) > 0.000001 ||
            abs(current.longitude - next.longitude) > 0.000001
        ) {
            selected = next.copy(address = draftAddress)
            recenterToken++
        }
    }

    val selectedPoint = selected?.let { QgMapPoint(it.latitude, it.longitude, 0) }
    val externalPoint = location
        ?.takeIf { validCustomerPoint(it.latitude, it.longitude) }
        ?.let { QgMapPoint(it.latitude, it.longitude, 0) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, QgLine, RoundedCornerShape(12.dp))
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
            Spacer(Modifier.size(8.dp))
            Text("เลือกที่อยู่จัดส่ง", fontWeight = FontWeight.ExtraBold)
        }

        Text(
            "ปักหมุดตำแหน่งรับสินค้า แล้วระบุรายละเอียดที่อยู่ด้านล่าง",
            color = QgMuted
        )
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF0F1F2))
                .border(1.dp, QgLine, RoundedCornerShape(14.dp))
        ) {
            QgLongdoLocationPickerMap(
                initialPoint = selectedPoint,
                recenterPoint = externalPoint,
                recenterToken = recenterToken,
                modifier = Modifier.fillMaxSize(),
                onCenterChanged = { point ->
                    selected = CustomerLocation(point.latitude, point.longitude, draftAddress)
                },
                onReady = { mapReady = it }
            )
            if (!mapReady) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onGps) {
            Text("ใช้ตำแหน่ง GPS ปัจจุบัน")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            selected?.let { "Lat: %.6f · Lng: %.6f".format(it.latitude, it.longitude) }
                ?: "Lat: -- · Lng: --",
            color = QgMuted
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draftAddress,
            onValueChange = {
                draftAddress = it
                selected = selected?.copy(address = it)
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("บ้านเลขที่ หมู่บ้าน ซอย ถนน หรือจุดสังเกต") }
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                selected?.let { onSave(it.copy(address = draftAddress.trim())) }
            },
            enabled = !busy && selected != null && draftAddress.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("บันทึกที่อยู่นี้", fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(26.dp))
    }
}

private fun validCustomerPoint(latitude: Double, longitude: Double): Boolean =
    latitude in 5.0..21.0 && longitude in 97.0..106.0 && !(latitude == 0.0 && longitude == 0.0)
