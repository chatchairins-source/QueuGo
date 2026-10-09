package com.queuego.rider

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Composable
import com.queuego.shared.NativeChatImage
import com.queuego.shared.prepareNativeChatImage

internal suspend fun prepareRiderChatImage(resolver: ContentResolver, uri: Uri): String =
    prepareNativeChatImage(resolver, uri)

@Composable
internal fun RiderChatImage(source: String) = NativeChatImage(source)
