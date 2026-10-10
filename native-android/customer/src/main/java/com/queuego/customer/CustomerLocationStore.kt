package com.queuego.customer

import android.content.Context
import com.queuego.shared.QgMapPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The same device-local delivery selection as Production qg_customer_delivery_location. */
internal class CustomerLocationStore(context: Context) {
    private val prefs = context.getSharedPreferences("queuego_customer_delivery_location", Context.MODE_PRIVATE)

    fun load(): CustomerLocation? = decodeCustomerDeliveryLocation(prefs.getString("location", null))

    suspend fun save(location: CustomerLocation) = withContext(Dispatchers.IO) {
        check(prefs.edit().putString("location", encodeCustomerDeliveryLocation(location)).commit()) {
            "บันทึกที่อยู่ในเครื่องไม่สำเร็จ"
        }
    }
}

internal fun encodeCustomerDeliveryLocation(location: CustomerLocation): String {
    require(QgMapPoint(location.latitude, location.longitude, 0).valid) { "พิกัดไม่ถูกต้อง" }
    return JSONObject().put("lat", location.latitude).put("lng", location.longitude)
        .put("address", location.address).toString()
}

internal fun decodeCustomerDeliveryLocation(raw: String?): CustomerLocation? = runCatching {
    val value = JSONObject(raw ?: return null)
    val lat = value.getDouble("lat")
    val lng = value.getDouble("lng")
    if (!QgMapPoint(lat, lng, 0).valid) return null
    CustomerLocation(lat, lng, value.optString("address", ""))
}.getOrNull()

/** A selected local pin must survive login or a late account-profile response. */
internal fun preferredCustomerDeliveryLocation(selected: CustomerLocation?, profile: CustomerLocation?): CustomerLocation? =
    selected?.takeIf { QgMapPoint(it.latitude, it.longitude, 0).valid }
        ?: profile?.takeIf { QgMapPoint(it.latitude, it.longitude, 0).valid }
