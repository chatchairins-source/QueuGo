package com.queuego.customer

import com.queuego.shared.QueueGoNativeMessagingService

class QueueGoCustomerMessagingService : QueueGoNativeMessagingService() {
    override val expectedRole = "customer"
    override val roleStoreKey = "customer"
    override val notificationIcon = R.drawable.qg_notification
    override val notificationLabel = "QueueGo"
}
