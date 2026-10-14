package com.queuego.rider

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed

internal const val RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT = 3

internal class RiderAcceptanceModeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("rider_acceptance_mode_v1", Context.MODE_PRIVATE)

    private fun autoKey(userId: String) = "auto_accept:" + userId
    private fun cashKey(userId: String) = "auto_accept_cash_limit:" + userId

    fun autoAcceptEnabled(userId: String): Boolean =
        prefs.getBoolean(autoKey(userId), false)

    fun setAutoAcceptEnabled(userId: String, enabled: Boolean) {
        prefs.edit().putBoolean(autoKey(userId), enabled).apply()
    }

    fun cashLimitBaht(userId: String): Double? {
        if (!prefs.contains(cashKey(userId))) return null
        return prefs.getString(cashKey(userId), null)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun setCashLimitBaht(userId: String, amount: Double) {
        require(amount.isFinite() && amount >= 0.0)
        prefs.edit().putString(cashKey(userId), amount.toString()).apply()
    }
}

internal fun riderAutoAcceptEligible(
    enabled: Boolean,
    networkAvailable: Boolean,
    online: Boolean,
    hasActiveWork: Boolean,
    actionBusy: Boolean,
    secondsLeft: Int,
    requiredMerchantCash: Double?,
    cashLimitBaht: Double?,
    multiShop: Boolean
): Boolean {
    val requiredCash = requiredMerchantCash
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?: return false
    val cashLimit = cashLimitBaht
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?: return false

    return enabled &&
        networkAvailable &&
        online &&
        !hasActiveWork &&
        !actionBusy &&
        !multiShop &&
        requiredCash <= cashLimit &&
        secondsLeft >= RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT
}

@Composable
internal fun RiderAcceptanceModeControl(
    autoAcceptEnabled: Boolean,
    cashLimitBaht: Double?,
    enabled: Boolean,
    onAutoAcceptChanged: (Boolean) -> Unit,
    onCashLimitChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCashDialog by remember { mutableStateOf(false) }
    var cashText by remember(cashLimitBaht) {
        mutableStateOf(cashLimitBaht?.let { if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(it) }.orEmpty())
    }
    var cashError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(
            1.dp,
            if (autoAcceptEnabled) Color(0xFFFFCDD3) else Color(0xFFEEE5E8)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (autoAcceptEnabled) Color(0xFFFFF3F5) else Color(0xFFFAF7F8)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "โหมดรับงาน",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = Color(0xFF211E20)
                )
                Text(
                    if (autoAcceptEnabled) {
                        "อัตโนมัติ · รับเฉพาะงานที่ QueueGo ส่งตรงถึงคุณ"
                    } else {
                        "เลือกเอง · ดูรายละเอียดแล้วกดรับภายใน 30 วินาที"
                    },
                    color = QgMuted,
                    fontSize = 8.5.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                TextButton(
                    onClick = {
                        cashText = cashLimitBaht?.let {
                            if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(it)
                        }.orEmpty()
                        cashError = null
                        showCashDialog = true
                    },
                    enabled = enabled,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        cashLimitBaht?.let { "เงินสดพร้อมจ่ายร้านสูงสุด ฿" + riderMoneyNumber(it) }
                            ?: "ตั้งวงเงินสดก่อนใช้รับอัตโนมัติ",
                        color = if (cashLimitBaht == null) QgRed else Color(0xFF4F484C),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (autoAcceptEnabled) {
                    Text(
                        "งานหลายร้าน งานพ่วง และฝากซักยังต้องกดรับเอง",
                        color = QgRed,
                        fontSize = 8.sp
                    )
                }
            }
            Switch(
                checked = autoAcceptEnabled,
                onCheckedChange = { checked ->
                    if (checked && cashLimitBaht == null) {
                        cashError = null
                        showCashDialog = true
                    } else {
                        onAutoAcceptChanged(checked)
                    }
                },
                enabled = enabled
            )
        }
    }

    if (showCashDialog) {
        AlertDialog(
            onDismissRequest = { showCashDialog = false },
            title = { Text("วงเงินสดสำหรับรับอัตโนมัติ", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text(
                        "ใส่จำนวนเงินสดสูงสุดที่คุณพร้อมจ่ายให้ร้านต่อหนึ่งงาน ระบบจะไม่รับอัตโนมัติถ้ายอดร้านสูงกว่านี้",
                        color = QgMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    OutlinedTextField(
                        value = cashText,
                        onValueChange = {
                            cashText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12)
                            cashError = null
                        },
                        label = { Text("บาท") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                    cashError?.let { Text(it, color = QgRed, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCashDialog = false }) { Text("ยกเลิก") }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = cashText.toDoubleOrNull()
                    if (amount == null || !amount.isFinite() || amount < 0.0) {
                        cashError = "กรุณาระบุจำนวนเงินที่ถูกต้อง"
                    } else {
                        onCashLimitChanged(amount)
                        if (!autoAcceptEnabled) onAutoAcceptChanged(true)
                        showCashDialog = false
                    }
                }) { Text("บันทึก") }
            }
        )
    }
}
