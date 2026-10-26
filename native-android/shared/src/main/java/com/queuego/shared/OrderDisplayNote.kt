package com.queuego.shared

/** Presentation only; keep raw order notes intact for server/status compatibility. */
fun cleanOrderDisplayNote(note: String?): String? = note?.lineSequence()
    ?.filter { it.isNotEmpty() && !it.startsWith("__QT_ORDER_STATUS__=") }
    ?.joinToString("\n")?.trim()?.takeIf { it.isNotEmpty() }
