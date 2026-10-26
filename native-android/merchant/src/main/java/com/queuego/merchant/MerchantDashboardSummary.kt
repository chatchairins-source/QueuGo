package com.queuego.merchant

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.chrono.ThaiBuddhistDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class MerchantDashboardSummary(
    val all: Int,
    val completed: Int,
    val preparing: Int,
    val cancelled: Int
)

internal fun merchantDashboardDateLabel(today: LocalDate = LocalDate.now()): String =
    ThaiBuddhistDate.from(today).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("th-TH")))

internal fun merchantDashboardSummary(
    orders: List<MerchantOrder>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): MerchantDashboardSummary {
    val todaysOrders = orders.filter { order ->
        val createdAt = order.createdAt ?: return@filter false
        val localDate = runCatching { Instant.parse(createdAt).atZone(zone).toLocalDate() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(createdAt).atZoneSameInstant(zone).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(createdAt).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(createdAt.take(10)) }.getOrNull()
        localDate == today
    }
    return MerchantDashboardSummary(
        all = todaysOrders.size,
        completed = todaysOrders.count { it.status in setOf("completed", "delivered") },
        preparing = todaysOrders.count { it.status in setOf("shop_accepted", "preparing") },
        cancelled = todaysOrders.count { it.status == "cancelled" }
    )
}
