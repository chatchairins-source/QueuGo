package com.queuego.ridernavigationmap

data class NavigationViewportInsets(
    val leftPx: Int = 0,
    val topPx: Int = 0,
    val rightPx: Int = 0,
    val bottomPx: Int = 0
) {
    fun normalized(): NavigationViewportInsets = NavigationViewportInsets(
        leftPx = leftPx.coerceAtLeast(0),
        topPx = topPx.coerceAtLeast(0),
        rightPx = rightPx.coerceAtLeast(0),
        bottomPx = bottomPx.coerceAtLeast(0)
    )

    val isEmpty: Boolean
        get() = leftPx <= 0 && topPx <= 0 && rightPx <= 0 && bottomPx <= 0
}

internal fun navigationViewportInsetsSignature(insets: NavigationViewportInsets): String {
    val safe = insets.normalized()
    return listOf(safe.leftPx, safe.topPx, safe.rightPx, safe.bottomPx).joinToString(":")
}
