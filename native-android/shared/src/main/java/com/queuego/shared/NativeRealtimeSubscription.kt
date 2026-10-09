package com.queuego.shared

/** Every subscription must have an ownership/selected-job equality filter; RLS still authorizes rows. */
data class NativeRealtimeSubscription(val table: String, val filter: String) {
    init {
        require(table.matches(Regex("[a-z_]+")))
        require(filter.matches(Regex("""[a-z_]+=eq\.[A-Za-z0-9_-]+""")))
    }
}

fun riderRealtimeSubscriptions(userId: String, profileId: String?): List<NativeRealtimeSubscription> =
    buildList {
        add(NativeRealtimeSubscription("notifications", "user_id=eq.$userId"))
        if (!profileId.isNullOrBlank()) {
            add(NativeRealtimeSubscription("orders", "rider_id=eq.$profileId"))
            add(NativeRealtimeSubscription("laundry_rider_jobs", "rider_id=eq.$profileId"))
            add(NativeRealtimeSubscription("laundry_rider_invites", "rider_id=eq.$profileId"))
            add(NativeRealtimeSubscription("laundry_rider_preferences", "rider_id=eq.$profileId"))
        }
    }

fun merchantRealtimeSubscriptions(userId: String, shopId: String): List<NativeRealtimeSubscription> = listOf(
    NativeRealtimeSubscription("notifications", "user_id=eq.$userId"),
    NativeRealtimeSubscription("orders", "shop_id=eq.$shopId")
)

fun customerRealtimeSubscriptions(userId: String): List<NativeRealtimeSubscription> = listOf(
    NativeRealtimeSubscription("notifications", "user_id=eq.$userId"),
    NativeRealtimeSubscription("orders", "customer_id=eq.$userId"),
    NativeRealtimeSubscription("market_orders", "customer_id=eq.$userId"),
    NativeRealtimeSubscription("laundry_orders", "customer_id=eq.$userId")
)
