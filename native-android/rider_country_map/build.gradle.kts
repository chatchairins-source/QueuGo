plugins {
    id("com.android.asset-pack")
}
assetPack {
    packName.set("rider_country_map")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
