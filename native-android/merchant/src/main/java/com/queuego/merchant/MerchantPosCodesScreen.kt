package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgSectionTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MerchantPosCodesScreen(
    auth: NativeAuth,
    api: MerchantPosApi,
    shopId: String,
    products: List<PosProduct>
) {
    val scope = rememberCoroutineScope()
    var codes by remember(auth.session.authUserId, shopId) {
        mutableStateOf<List<PosProductCode>>(emptyList())
    }
    var loading by remember(auth.session.authUserId, shopId) { mutableStateOf(true) }
    var writing by remember(auth.session.authUserId, shopId) { mutableStateOf(false) }
    var error by remember(auth.session.authUserId, shopId) { mutableStateOf<String?>(null) }
    var productSearch by remember(auth.session.authUserId, shopId) { mutableStateOf("") }
    var selectedProductId by remember(auth.session.authUserId, shopId) { mutableStateOf<String?>(null) }
    var codeType by remember(auth.session.authUserId, shopId) { mutableStateOf("barcode") }
    var codeText by remember(auth.session.authUserId, shopId) { mutableStateOf("") }
    var deleteTarget by remember(auth.session.authUserId, shopId) { mutableStateOf<PosProductCode?>(null) }

    suspend fun reloadCodes() {
        loading = true
        error = null
        try {
            codes = api.productCodes(auth)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "อ่านรหัสสินค้าไม่สำเร็จ"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(shopId, auth.session.authUserId) { reloadCodes() }
    val selected = products.firstOrNull { it.id == selectedProductId }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QgSectionTitle("รหัสสินค้า", "เชื่อมบาร์โค้ดหรือ SKU กับสินค้าในร้านจริง")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("เพิ่มรหัสสินค้า", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                OutlinedTextField(
                    productSearch,
                    onValueChange = { productSearch = it },
                    label = { Text("ค้นหาชื่อสินค้าในร้าน") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (selected != null) {
                    Text("สินค้าที่เลือก: " + selected.name,
                        color = QgRed, fontWeight = FontWeight.Bold)
                } else {
                    Text("เลือกสินค้าให้ชัดเจนก่อนบันทึกรหัส",
                        color = QgMuted, fontSize = 12.sp)
                }
                val candidates = products.filter {
                    productSearch.isBlank() ||
                        it.name.contains(productSearch.trim(), ignoreCase = true)
                }.take(8)
                candidates.forEach { product ->
                    OutlinedButton(
                        onClick = { selectedProductId = product.id },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !writing
                    ) {
                        Text(
                            (if (product.id == selectedProductId) "✓ " else "") + product.name,
                            maxLines = 2
                        )
                    }
                }
                if (products.size > candidates.size) {
                    Text("แสดงสูงสุด 8 รายการ · พิมพ์ชื่อเพื่อค้นหาเพิ่มเติม",
                        color = QgMuted, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("barcode" to "บาร์โค้ด", "sku" to "SKU").forEach { (type, label) ->
                        if (codeType == type) {
                            Button(onClick = { codeType = type }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = QgRed)) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(onClick = { codeType = type },
                                modifier = Modifier.weight(1f)) { Text(label) }
                        }
                    }
                }
                OutlinedTextField(
                    codeText,
                    onValueChange = { codeText = it.take(64) },
                    label = { Text(if (codeType == "barcode") "เลขบาร์โค้ด 8–14 หลัก" else "รหัส SKU") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (codeType == "barcode") KeyboardType.Number else KeyboardType.Ascii
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val product = selected ?: return@Button
                        writing = true
                        error = null
                        scope.launch {
                            try {
                                api.saveProductCode(auth, product.id, codeType, codeText)
                                codeText = ""
                                reloadCodes()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "บันทึกรหัสสินค้าไม่สำเร็จ"
                            } finally {
                                writing = false
                            }
                        }
                    },
                    enabled = selected != null && codeText.isNotBlank() && !loading && !writing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                ) { Text("บันทึกรหัสสินค้า", fontWeight = FontWeight.Bold) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("รหัสสินค้าที่บันทึก", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            OutlinedButton(onClick = { scope.launch { reloadCodes() } },
                enabled = !loading && !writing) { Text("โหลดใหม่") }
        }
        if (error != null) Text(error.orEmpty(), color = QgRed)
        if (loading) {
            Text("กำลังอ่านรหัสจากเซิร์ฟเวอร์", color = QgMuted)
        } else if (codes.isEmpty() && error == null) {
            Text("ยังไม่มีรหัสสินค้า", color = QgMuted)
        }
        codes.forEach { code ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(code.productName, fontWeight = FontWeight.Bold)
                        Text(code.code + " · " + if (code.type == "barcode") "บาร์โค้ด" else "SKU",
                            color = QgMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { deleteTarget = code }, enabled = !writing) {
                        Text("ลบ", color = QgRed)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { if (!writing) deleteTarget = null },
            title = { Text("ลบรหัสสินค้า") },
            text = { Text(target.productName + " · " + target.code + "\nยืนยันลบรหัสนี้ออกจากร้าน?") },
            confirmButton = {
                TextButton(
                    enabled = !writing,
                    onClick = {
                        writing = true
                        scope.launch {
                            try {
                                api.removeProductCode(auth, target.id)
                                deleteTarget = null
                                reloadCodes()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "ลบรหัสไม่สำเร็จ"
                            } finally {
                                writing = false
                            }
                        }
                    }
                ) { Text("ยืนยันลบ", color = QgRed) }
            },
            dismissButton = {
                TextButton(enabled = !writing, onClick = { deleteTarget = null }) {
                    Text("ยกเลิก")
                }
            }
        )
    }
}
