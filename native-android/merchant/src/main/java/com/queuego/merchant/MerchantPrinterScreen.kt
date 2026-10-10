package com.queuego.merchant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgCard
import com.queuego.shared.QgGreen
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle

@Composable
fun MerchantPrinterScreen(
    snapshot: PosSnapshot,
    settings: MerchantPrinterSettings,
    busy: Boolean,
    onSave: (MerchantPrinterSettings) -> Unit,
    onTest: (MerchantPrinterSettings) -> Unit,
    onKitchen: (PosBill) -> Unit,
    onReceipt: (PosBill) -> Unit
) {
    var width by remember(settings.widthMm) { mutableStateOf(settings.widthMm) }
    var bridgeUrl by remember(settings.bridgeUrl) { mutableStateOf(settings.bridgeUrl) }
    var autoKitchen by remember(settings.autoKitchen) { mutableStateOf(settings.autoKitchen) }
    var autoReceipt by remember(settings.autoReceipt) { mutableStateOf(settings.autoReceipt) }
    var localError by remember { mutableStateOf<String?>(null) }

    fun draft(): MerchantPrinterSettings = MerchantPrinterSettings(
        widthMm = width,
        bridgeUrl = bridgeUrl.trim(),
        autoKitchen = autoKitchen,
        autoReceipt = autoReceipt
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        QgSectionTitle("เครื่องพิมพ์", "Android Native ใช้ HTTPS Print Bridge เท่านั้น")
        Spacer(Modifier.height(10.dp))

        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("ตั้งค่า Print Bridge", fontWeight = FontWeight.ExtraBold)
                Text(
                    "QueueGo จะส่งข้อความใบครัว/ใบเสร็จไปยัง Bridge ที่ร้านควบคุมผ่าน HTTPS โดยไม่เปิด Web Bluetooth หรือ WebUSB",
                    color = QgMuted
                )
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = bridgeUrl,
                    onValueChange = { bridgeUrl = it.take(500); localError = null },
                    label = { Text("HTTPS Print Bridge URL") },
                    placeholder = { Text("https://printer.example.com/print") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("ขนาดกระดาษ", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = width == 58, onClick = { width = 58 }, enabled = !busy)
                    Text("58 มม.")
                    RadioButton(selected = width == 80, onClick = { width = 80 }, enabled = !busy)
                    Text("80 มม.")
                }
                PrinterSettingCheck(
                    checked = autoKitchen,
                    enabled = !busy,
                    label = "พิมพ์ใบครัวอัตโนมัติเมื่อส่งเข้าครัว"
                ) { autoKitchen = it }
                PrinterSettingCheck(
                    checked = autoReceipt,
                    enabled = !busy,
                    label = "พิมพ์ใบเสร็จอัตโนมัติเมื่อชำระเงินสำเร็จ"
                ) { autoReceipt = it }

                if (!localError.isNullOrBlank()) {
                    Text(localError!!, color = QgRed)
                    Spacer(Modifier.height(6.dp))
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val value = draft()
                            try {
                                value.validated()
                                localError = null
                                onSave(value)
                            } catch (failure: IllegalArgumentException) {
                                localError = failure.message
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("บันทึก") }

                    OutlinedButton(
                        onClick = {
                            val value = draft()
                            try {
                                value.validated()
                                require(value.bridgeUrl.isNotBlank()) { "กรุณากรอก HTTPS Print Bridge URL" }
                                localError = null
                                onTest(value)
                            } catch (failure: IllegalArgumentException) {
                                localError = failure.message
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (busy) CircularProgressIndicator()
                        else Text("ทดสอบพิมพ์")
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        QgSectionTitle("พิมพ์ซ้ำ", "บิลล่าสุดจาก Production POS")
        Spacer(Modifier.height(8.dp))

        val recent = snapshot.bills.take(30)
        if (recent.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) {
                Text("ยังไม่มีบิลสำหรับพิมพ์", color = QgMuted)
            }
        } else {
            recent.forEach { bill ->
                val kitchen = merchantKitchenPrintKey(snapshot, bill) != null
                val receipt = merchantReceiptPrintKey(snapshot, bill) != null
                QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bill.number, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    bill.kitchenStatus + " · " + bill.paymentStatus,
                                    color = QgMuted
                                )
                            }
                            Text(
                                "฿" + String.format(java.util.Locale.US, "%,.2f", bill.total),
                                color = QgRed,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onKitchen(bill) },
                                enabled = !busy && kitchen,
                                modifier = Modifier.weight(1f)
                            ) { Text("พิมพ์ใบครัว") }
                            OutlinedButton(
                                onClick = { onReceipt(bill) },
                                enabled = !busy && receipt,
                                modifier = Modifier.weight(1f)
                            ) { Text("พิมพ์ใบเสร็จ") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "สถานะ Bridge: " + if (settings.bridgeUrl.isBlank()) "ยังไม่ตั้งค่า" else "ตั้งค่าแล้ว",
            color = if (settings.bridgeUrl.isBlank()) QgMuted else QgGreen,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PrinterSettingCheck(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled
        )
        Text(label, modifier = Modifier.weight(1f))
    }
}
