package com.queuego.merchant

import com.queuego.shared.QueueGoNativeMessagingService

class QueueGoMerchantMessagingService : QueueGoNativeMessagingService() {
    override val expectedRole = "shop"
    override val roleStoreKey = "shop"
    override val notificationIcon = R.drawable.qg_notification
    override val notificationLabel = "QueueGo Merchant"
    override val highImportance = true
}
