package com.queuego.customer

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

// Display formatting only; checkout amounts and validation remain server-owned.
internal fun customerNumber(value: Double, fractionDigits: Int = 3): String =
    if (!value.isFinite()) "—" else NumberFormat.getNumberInstance(Locale("th", "TH")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = fractionDigits
        roundingMode = RoundingMode.HALF_UP
    }.format(value)
internal fun customerBaht(value: Double) = if (value.isFinite()) "฿" + customerNumber(value) else "รอสรุปราคา"
internal fun laundryMoney(value: Double) = if (value.isFinite()) customerNumber(value, 2) + " บาท" else "รอสรุป"
internal fun laundryCheckoutMessage(message: String?): String = when {
    message?.contains("OUTSIDE_SERVICE_AREA") == true -> "ตำแหน่งรับผ้าอยู่นอกพื้นที่ให้บริการ QueueGo Pilot"
    message?.contains("DELIVERY_DISTANCE_EXCEEDED") == true -> "ร้านซักและจุดรับผ้าอยู่ห่างเกินขอบเขตที่ QueueGo Pilot ให้บริการ"
    else -> message ?: "ส่งคำขอฝากซักไม่สำเร็จ"
}
