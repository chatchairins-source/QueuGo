package com.queuego.rider

internal data class RiderRegistrationForm(
    val name: String = "", val phone: String = "", val email: String = "",
    val vehicle: String = "motorcycle", val capacity: String = "", val plate: String = "",
    val make: String = "", val model: String = "", val province: String = "",
    val district: String = "", val area: String = ""
) {
    val normalizedPhone get() = phone.replace(Regex("[-\\s]"), "")
    fun validate(step: Int, password: String, documents: Set<String>, resuming: Boolean = false): String? {
        if (step == 0) {
            if (name.isBlank() || phone.isBlank() || (!resuming && password.isBlank())) return "กรุณากรอกข้อมูลส่วนตัวให้ครบ"
            if (!normalizedPhone.matches(Regex("0[0-9]{9}"))) return "กรุณากรอกเบอร์โทรศัพท์ 10 หลัก"
            if (!resuming && !strongRiderPassword(password)) return RIDER_PASSWORD_MESSAGE
            if (email.isNotBlank() && !email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) return "กรุณากรอกอีเมลให้ถูกต้อง"
            if ("rr-photo" !in documents) return "กรุณาอัปโหลดรูปถ่ายหน้าตรง"
        }
        if (step == 1) {
            if (plate.isBlank() || province.isBlank() || district.isBlank()) return "กรุณากรอกทะเบียนรถ จังหวัด และอำเภอ/เขต"
            val kg = capacity.ifBlank { "0" }.toDoubleOrNull()
            if (kg == null || !kg.isFinite() || kg < 0 || kg > 1000) return "ความจุรถไม่ถูกต้อง"
        }
        if (step == 2 && !RIDER_DOCUMENTS.keys.all { it in documents }) return "กรุณาอัปโหลดเอกสารบังคับให้ครบ"
        return null
    }
}
internal const val RIDER_PASSWORD_MESSAGE = "รหัสผ่านต้องมีอย่างน้อย 12 ตัว และมี A-Z, a-z, ตัวเลข และสัญลักษณ์"
internal fun strongRiderPassword(password: String): Boolean = password.length >= 12 &&
    Regex("[a-z]").containsMatchIn(password) && Regex("[A-Z]").containsMatchIn(password) &&
    Regex("[0-9]").containsMatchIn(password) && Regex("[^A-Za-z0-9]").containsMatchIn(password)
internal val RIDER_DOCUMENTS = linkedMapOf(
    "rr-photo" to "รูปถ่ายหน้าตรง", "rr-id-front" to "บัตรประชาชนด้านหน้า",
    "rr-id-back" to "บัตรประชาชนด้านหลัง", "rr-license" to "ใบขับขี่",
    "rr-prb" to "พ.ร.บ. รถ", "rr-vehicle-photo" to "รูปถ่ายคู่กับรถ"
)
