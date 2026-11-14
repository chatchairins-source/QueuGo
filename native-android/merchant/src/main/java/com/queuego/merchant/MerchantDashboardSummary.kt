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

internal fun merchantOrderFilterBucket(status: String, riderArrivedCustomerAt: String? = null): String {
    val normalized = status.lowercase()
    val state = if (normalized in setOf("in_progress", "delivering", "rider_to_customer") &&
        !riderArrivedCustomerAt.isNullOrBlank()
    ) "arrived" else normalized
    return when (state) {
        "pending", "pending_shop" -> "pending"
        "searching_rider" -> "searching"
        "shop_accepted", "accepted", "rider_to_shop", "assigned", "rider_assigned", "preparing" -> "preparing"
        "waiting_rider", "ready" -> "ready"
        "picked_up", "delivering", "in_progress", "rider_to_customer", "arrived" -> "delivering"
        "completed", "delivered" -> "done"
        "cancelled", "no_rider_available" -> "cancel"
        else -> "processing"
    }
}

internal fun merchantOrderSearchMatches(order: MerchantOrder, query: String): Boolean {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return true
    return listOf(order.number, order.customerName.orEmpty()).any { it.lowercase(Locale.ROOT).contains(needle) }
}

internal fun merchantOrderProgressStage(status: String): Int = when (status.lowercase()) {
    "completed", "delivered" -> 4
    "picked_up", "delivering", "in_progress", "rider_to_customer", "arrived" -> 3
    "rider_to_shop", "preparing", "ready", "waiting_rider", "assigned", "rider_assigned" -> 2
    "shop_accepted", "searching_rider", "accepted" -> 1
    else -> 0
}

internal fun merchantOrderCanReportIssue(status: String): Boolean = status.equals("completed", ignoreCase = true)

internal fun merchantOrderShowsProgress(status: String): Boolean =
    status.lowercase() !in setOf("cancelled", "no_rider_available")

internal fun merchantOrderTimeLabel(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""
    val instant = runCatching { Instant.parse(createdAt) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(createdAt).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(createdAt).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
        ?: return ""
    return DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("th-TH"))
        .format(instant.atZone(ZoneId.systemDefault())) + " น."
}

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
