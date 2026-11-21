plugins {
    id("com.android.asset-pack")
}
assetPack {
    packName.set("rider_country_routing")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
