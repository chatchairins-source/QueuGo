package com.queuego.customer

import android.content.Context
import com.queuego.shared.QgMapPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val CUSTOMER_LOCATION_MAX_JSON_CHARS = 64 * 1024

/** The same device-local delivery selection as Production qg_customer_delivery_location. */
internal class CustomerLocationStore(context: Context) {
    private val prefs = context.getSharedPreferences("queuego_customer_delivery_location", Context.MODE_PRIVATE)

    fun load(): CustomerLocation? {
        val raw = prefs.getString("location", null) ?: return null
        if (raw.length > CUSTOMER_LOCATION_MAX_JSON_CHARS) {
            prefs.edit().remove("location").apply()
            return null
        }
        return decodeCustomerDeliveryLocation(raw)
    }

    suspend fun save(location: CustomerLocation) = withContext(Dispatchers.IO) {
        val raw = encodeCustomerDeliveryLocation(location)
        require(raw.length <= CUSTOMER_LOCATION_MAX_JSON_CHARS) { "ที่อยู่ในเครื่องมีขนาดใหญ่เกินกำหนด" }
        check(prefs.edit().putString("location", raw).commit()) {
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
