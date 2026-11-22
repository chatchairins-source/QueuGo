package com.queuego.merchant

import java.security.MessageDigest

/** Preserve legacy empty-reference broadcasts while distinguishing absent FCM ids. */
internal fun merchantPushMessageKey(messageId: String?, referenceId: String, type: String, fallbackId: String): String =
    messageId?.takeIf { it.isNotBlank() }?.let { "fcm:${it.length}:$it" }
        ?: referenceId.takeIf { it.isNotBlank() }?.let { "reference:${it.length}:$it|type:${type.length}:$type" }
        ?: "unidentified:${fallbackId.length}:$fallbackId"

/** Android ignores Intent extras for PendingIntent identity. Use the full digest
 * in both the Intent data and Notification tag, never a 32-bit String hash.
 * Length-prefix fields to distinguish account/message delimiter combinations.
 */
internal fun merchantPushIdentity(accountId: String, messageKey: String): String {
    val input = "account:${accountId.length}:$accountId|message:${messageKey.length}:$messageKey"
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return "queuego://merchant/push/" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
