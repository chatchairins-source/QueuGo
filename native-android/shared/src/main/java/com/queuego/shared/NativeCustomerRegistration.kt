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
