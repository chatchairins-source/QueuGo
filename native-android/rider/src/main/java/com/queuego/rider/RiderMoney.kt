package com.queuego.rider

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Display only: matches Web th-TH numbers; never round values used in RPCs. */
internal fun riderMoneyNumber(value: Double?, fixedCashDecimals: Boolean = false): String {
    if (value == null || !value.isFinite()) return "—"
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("th-TH")).apply {
        minimumFractionDigits = if (fixedCashDecimals) 2 else 0
        maximumFractionDigits = if (fixedCashDecimals) 2 else 3
        roundingMode = RoundingMode.HALF_UP
    }
    return format.format(BigDecimal.valueOf(value))
}

internal fun riderMoneyBaht(value: Double?, fixedCashDecimals: Boolean = false): String =
    "฿" + riderMoneyNumber(value, fixedCashDecimals)
