package com.queuego.rider

import com.queuego.shared.NATIVE_PASSWORD_POLICY_MESSAGE
import com.queuego.shared.isNativeStrongPassword

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
            if (!resuming && !isNativeStrongPassword(password)) return NATIVE_PASSWORD_POLICY_MESSAGE
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
internal val RIDER_DOCUMENTS = linkedMapOf(
    "rr-photo" to "รูปถ่ายหน้าตรง", "rr-id-front" to "บัตรประชาชนด้านหน้า",
    "rr-id-back" to "บัตรประชาชนด้านหลัง", "rr-license" to "ใบขับขี่",
    "rr-prb" to "พ.ร.บ. รถ", "rr-vehicle-photo" to "รูปถ่ายคู่กับรถ"
)
