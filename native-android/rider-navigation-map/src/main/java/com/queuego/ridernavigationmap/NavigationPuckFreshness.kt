package com.queuego.ridernavigationmap

enum class NavigationPuckFreshness {
    FRESH,
    STALE,
    LOST,
    UNKNOWN
}

data class NavigationPuckFreshnessState(
    val freshness: NavigationPuckFreshness,
    val ageMillis: Long?
)

fun navigationPuckFreshness(
    fix: NavigationMapFix?,
    nowElapsedRealtimeNanos: Long,
    staleAfterMillis: Long = 5_000L,
    lostAfterMillis: Long = 15_000L
): NavigationPuckFreshnessState {
    val fixNanos = fix?.elapsedRealtimeNanos ?: 0L
    if (fixNanos <= 0L || nowElapsedRealtimeNanos <= 0L) {
        return NavigationPuckFreshnessState(
            freshness = NavigationPuckFreshness.UNKNOWN,
            ageMillis = null
        )
    }

    val ageNanos = (nowElapsedRealtimeNanos - fixNanos).coerceAtLeast(0L)
    val ageMillis = ageNanos / 1_000_000L
    val staleAt = staleAfterMillis.coerceAtLeast(1L)
    val lostAt = lostAfterMillis.coerceAtLeast(staleAt + 1L)

    val freshness = when {
        ageMillis >= lostAt -> NavigationPuckFreshness.LOST
        ageMillis >= staleAt -> NavigationPuckFreshness.STALE
        else -> NavigationPuckFreshness.FRESH
    }
    return NavigationPuckFreshnessState(
        freshness = freshness,
        ageMillis = ageMillis
    )
}
