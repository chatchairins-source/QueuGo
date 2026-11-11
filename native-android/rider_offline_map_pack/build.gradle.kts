plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("rider_offline_map_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
