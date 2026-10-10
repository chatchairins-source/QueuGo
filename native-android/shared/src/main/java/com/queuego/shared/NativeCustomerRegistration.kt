package com.queuego.shared

data class NativeCustomerRegistration(
    val name: String,
    val phone: String,
    val email: String?,
    val password: String,
    val confirmation: String,
    val latitude: Double,
    val longitude: Double
) {
    fun validate() {
        require(name.trim().isNotBlank() &&
            (if (email == null) phone.filter(Char::isDigit).length >= 9
            else Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()))) {
            "กรอกข้อมูลสมัครสมาชิกให้ครบ"
        }
        requireNativeStrongPassword(password)
        require(password == confirmation) { "ยืนยันรหัสผ่านให้ตรงกัน" }
        require(latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "ต้องอนุญาตตำแหน่งที่ตั้งเพื่อสมัคร"
        }
    }
}

const val NATIVE_PASSWORD_POLICY_MESSAGE =
    "รหัสผ่านต้องมีอย่างน้อย 12 ตัว และมี A-Z, a-z, ตัวเลข และสัญลักษณ์"

fun requireNativeStrongPassword(password: String) {
    require(password.length >= 12 && password.any { it in 'a'..'z' } &&
        password.any { it in 'A'..'Z' } && password.any { it in '0'..'9' } &&
        password.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' }) {
        NATIVE_PASSWORD_POLICY_MESSAGE
    }
}

/** Account creation succeeded: recover by signing in, never automatically repeat signup. */
class NativeCustomerSignupCompletedException(cause: Exception) : Exception(
    "สร้างบัญชีแล้ว กรุณาเข้าสู่ระบบ หากสมัครด้วยอีเมลให้ตรวจอีเมลยืนยันบัญชี: " +
        (cause.message ?: "เชื่อมต่อไม่สำเร็จ"), cause
)

class NativeCustomerSignupUncertainException(cause: Exception) : Exception(
    "ยังยืนยันผลสมัครไม่ได้ กรุณาลองเข้าสู่ระบบก่อนสมัครซ้ำ: " +
        (cause.message ?: "เชื่อมต่อไม่สำเร็จ"), cause
)

const val NATIVE_MERCHANT_TERMS_VERSION = "beta-2026-10-02-v1"

val NATIVE_MERCHANT_CATEGORIES = setOf(
    "food", "grocery", "cafe", "laundry", "market", "shopping", "other"
)

val NATIVE_MERCHANT_SHOPPING_SUBCATEGORIES = setOf(
    "mobile_accessories",
    "computer_it",
    "automotive_car",
    "automotive_motorcycle",
    "fashion_accessories",
    "toys",
    "home_decor"
)

data class NativeMerchantMarketRequest(
    val name: String,
    val province: String,
    val district: String?,
    val subdistrict: String?,
    val address: String?
)

data class NativeMerchantMarketRegistration(
    val latitude: Double,
    val longitude: Double,
    val marketId: String?,
    val stallNo: String?,
    val zone: String?,
    val request: NativeMerchantMarketRequest?,
    val confirmedPlace: Boolean,
    val confirmedSeller: Boolean
)

data class NativeMerchantRegistration(
    val contactName: String,
    val shopName: String,
    val category: String,
    val shoppingSubcategories: Set<String>,
    val phone: String,
    val password: String,
    val confirmation: String,
    val truthConfirmed: Boolean,
    val betaAcknowledged: Boolean,
    val marketRegistration: NativeMerchantMarketRegistration?
) {
    val normalizedPhone: String get() = phone.replace(Regex("[-\\s]"), "")

    fun validate() {
        require(contactName.trim().isNotBlank() &&
            shopName.trim().length in 2..100 &&
            category in NATIVE_MERCHANT_CATEGORIES &&
            Regex("^0[0-9]{9}$").matches(normalizedPhone)) {
            "กรุณากรอกชื่อร้าน เลือกประเภทร้าน และตรวจข้อมูลสมัครให้ครบ"
        }
        if (category == "shopping") {
            require(shoppingSubcategories.isNotEmpty() &&
                shoppingSubcategories.all { it in NATIVE_MERCHANT_SHOPPING_SUBCATEGORIES }) {
                "กรุณาเลือกหมวดย่อยของร้านช้อปปิ้งอย่างน้อย 1 หมวด"
            }
        }
        requireNativeStrongPassword(password)
        require(password == confirmation) { "ยืนยันรหัสผ่านให้ตรงกัน" }
        require(truthConfirmed && betaAcknowledged) {
            "กรุณาอ่านและยอมรับข้อตกลงการทดสอบ QueueGo ก่อนสมัคร"
        }
        if (category == "market") {
            val market = requireNotNull(marketRegistration) {
                "กรุณากดค้นหาตลาดใกล้ร้านและอนุญาตพิกัดก่อนสมัคร"
            }
            require(market.latitude.isFinite() && market.longitude.isFinite() &&
                market.latitude in -90.0..90.0 && market.longitude in -180.0..180.0) {
                "พิกัดร้านไม่ถูกต้อง กรุณาลองใหม่"
            }
            require(market.confirmedPlace && market.confirmedSeller) {
                "กรุณายืนยันว่าร้านอยู่ในตลาดและคุณเป็นผู้ค้าจริง"
            }
            if (market.marketId.isNullOrBlank()) {
                val request = requireNotNull(market.request) {
                    "ไม่พบตลาดใกล้ร้าน กรุณาระบุชื่อตลาดและจังหวัดเพื่อส่งให้ Admin ตรวจสอบ"
                }
                require(request.name.trim().length >= 2 && request.province.trim().length >= 2) {
                    "ไม่พบตลาดใกล้ร้าน กรุณาระบุชื่อตลาดและจังหวัดเพื่อส่งให้ Admin ตรวจสอบ"
                }
            }
        }
    }
}

data class NativeRegistrationMarket(
    val id: String,
    val name: String,
    val address: String?,
    val province: String?,
    val district: String?,
    val subdistrict: String?,
    val latitude: Double,
    val longitude: Double,
    val assignmentRadiusKm: Double,
    val distanceKm: Double
) {
    val selectable: Boolean get() = distanceKm <= assignmentRadiusKm
}

fun nativeDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earth = 6371.0
    fun rad(value: Double) = value * Math.PI / 180.0
    val dLat = rad(lat2 - lat1)
    val dLng = rad(lng2 - lng1)
    val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(rad(lat1)) * kotlin.math.cos(rad(lat2)) *
        kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
    return earth * 2 * kotlin.math.asin(kotlin.math.min(1.0, kotlin.math.sqrt(h)))
}

class NativeMerchantSignupCompletedException(cause: Exception) : Exception(
    "สร้างบัญชีร้านค้าแล้ว แต่ขั้นตอนตั้งค่าร้านยังไม่สมบูรณ์ กรุณากดลองสมัครอีกครั้งเพื่อทำต่อโดยไม่สร้างบัญชีซ้ำ: " +
        (cause.message ?: "เชื่อมต่อไม่สำเร็จ"), cause
)

class NativeMerchantSignupUncertainException(cause: Exception) : Exception(
    "ยังยืนยันผลสมัครไม่ได้ กรุณาลองอีกครั้ง ระบบจะตรวจบัญชีเดิมก่อนและจะไม่สร้างซ้ำอัตโนมัติ: " +
        (cause.message ?: "เชื่อมต่อไม่สำเร็จ"), cause
)

