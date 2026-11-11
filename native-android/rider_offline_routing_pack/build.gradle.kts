plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("rider_offline_routing_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
