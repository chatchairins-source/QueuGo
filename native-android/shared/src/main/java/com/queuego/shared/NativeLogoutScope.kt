package com.queuego.shared

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope

/** Logout must outlive the composable that disappears when local session storage is cleared. */
fun nativeLogoutScope(context: Context, fallback: CoroutineScope): CoroutineScope {
    var candidate = context
    while (true) {
        if (candidate is LifecycleOwner) return candidate.lifecycleScope
        val wrapper = candidate as? ContextWrapper ?: return fallback
        val next = wrapper.baseContext
        if (next === candidate) return fallback
        candidate = next
    }
}
