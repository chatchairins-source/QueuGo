package com.queuego.merchant

import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PosProduct(
    val id: String,
    val name: String,
    val price: Double,
    val image: String?,
    val available: Boolean
)

data class PosTable(
    val id: String,
    val label: String,
    val active: Boolean
)

data class PosBill(
    val id: String,
    val number: String,
    val type: String,
    val tableId: String?,
    val kitchenStatus: String,
    val paymentStatus: String,
    val total: Double,
    val discount: Double
)

data class PosLine(
    val productId: String?,
    val name: String,
    val description: String?,
    val quantity: Int,
    val total: Double
)

data class PosSnapshot(
    val shopId: String,
    val products: List<PosProduct>,
    val tables: List<PosTable>,
    val bills: List<PosBill>,
    val linesByOrder: Map<String, List<PosLine>>
)

class MerchantPosApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun snapshot(auth: NativeAuth): PosSnapshot {
        val rawShop = http.rpc("pos_my_shop", auth.session.accessToken, JSONObject())
        val shopId = when (rawShop) {
            is String -> rawShop
            is JSONObject -> rawShop.optString("pos_my_shop").ifBlank { rawShop.optString("id") }
            is JSONArray -> rawShop.optString(0)
            else -> rawShop.toString().trim('"')
        }
        require(shopId.isNotBlank() && shopId != "null") { "บัญชีนี้ยังไม่มีสิทธิ์ POS ของร้าน" }

        val tablesRaw = http.array(http.get(
            "pos_tables?select=id,label,active&shop_id=eq." + http.enc(shopId) + "&order=label.asc",
            auth.session.accessToken
        ))
        val productsRaw = http.array(http.get(
            "products?select=id,name,price,pos_price,image,available,pos_available&shop_id=eq." +
                http.enc(shopId) + "&order=name.asc",
            auth.session.accessToken
        ))
        val billsRaw = http.array(http.get(
            "orders?select=id,order_number,order_type,table_id,kitchen_status,payment_status,total_amount,discount_amount" +
                "&shop_id=eq." + http.enc(shopId) +
                "&sales_channel=eq.POS&payment_status=eq.UNPAID&status=neq.cancelled&order=created_at.desc&limit=100",
            auth.session.accessToken
        ))

        val billIds = buildList {
            for (i in 0 until billsRaw.length()) {
                billsRaw.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        val itemsRaw = if (billIds.isEmpty()) JSONArray() else http.array(http.get(
            "order_items?select=order_id,product_id,item_name,description,quantity,total_price" +
                "&order_id=in.(" + billIds.joinToString(",") + ")&order=created_at.asc",
            auth.session.accessToken
        ))

        val lines = mutableMapOf<String, MutableList<PosLine>>()
        for (i in 0 until itemsRaw.length()) {
            val r = itemsRaw.optJSONObject(i) ?: continue
            val orderId = r.optString("order_id")
            if (orderId.isBlank()) continue
            lines.getOrPut(orderId) { mutableListOf() }.add(
                PosLine(
                    productId = r.optString("product_id").takeIf { it.isNotBlank() && it != "null" },
                    name = r.optString("item_name").ifBlank { "สินค้า" },
                    description = r.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    quantity = r.optInt("quantity", 1).coerceAtLeast(1),
                    total = r.optDouble("total_price", 0.0)
                )
            )
        }

        val products = buildList {
            for (i in 0 until productsRaw.length()) {
                val r = productsRaw.optJSONObject(i) ?: continue
                val posAvailable = if (r.has("pos_available") && !r.isNull("pos_available")) r.optBoolean("pos_available") else true
                val normalAvailable = r.optBoolean("available", true)
                if (!normalAvailable || !posAvailable) continue
                add(
                    PosProduct(
                        id = r.optString("id"),
                        name = r.optString("name").ifBlank { "สินค้า" },
                        price = if (r.has("pos_price") && !r.isNull("pos_price")) r.optDouble("pos_price") else r.optDouble("price", 0.0),
                        image = r.optString("image").takeIf { it.isNotBlank() && it != "null" },
                        available = true
                    )
                )
            }
        }

        val tables = buildList {
            for (i in 0 until tablesRaw.length()) {
                val r = tablesRaw.optJSONObject(i) ?: continue
                add(PosTable(r.optString("id"), r.optString("label").ifBlank { "โต๊ะ" }, r.optBoolean("active", true)))
            }
        }

        val bills = buildList {
            for (i in 0 until billsRaw.length()) {
                val r = billsRaw.optJSONObject(i) ?: continue
                val id = r.optString("id")
                if (id.isBlank()) continue
                add(
                    PosBill(
                        id = id,
                        number = orderNumber(r.optString("order_number"), id),
                        type = r.optString("order_type").ifBlank { "TAKEAWAY" },
                        tableId = r.optString("table_id").takeIf { it.isNotBlank() && it != "null" },
                        kitchenStatus = r.optString("kitchen_status").ifBlank { "NEW" },
                        paymentStatus = r.optString("payment_status").ifBlank { "UNPAID" },
                        total = r.optDouble("total_amount", 0.0),
                        discount = r.optDouble("discount_amount", 0.0)
                    )
                )
            }
        }

        return PosSnapshot(shopId, products, tables, bills, lines)
    }

    suspend fun addProduct(
        auth: NativeAuth,
        currentBillId: String?,
        type: String,
        tableId: String?,
        productId: String,
        note: String
    ): String {
        val raw = if (currentBillId.isNullOrBlank()) {
            http.rpc(
                "pos_create_bill_once",
                auth.session.accessToken,
                JSONObject()
                    .put("p_request", UUID.randomUUID().toString())
                    .put("p_type", type)
                    .put("p_table", tableId ?: JSONObject.NULL)
                    .put("p_product", productId)
                    .put("p_note", note.trim())
            )
        } else {
            http.rpc(
                "pos_edit_bill",
                auth.session.accessToken,
                JSONObject()
                    .put("p_order", currentBillId)
                    .put("p_type", type)
                    .put("p_table", tableId ?: JSONObject.NULL)
                    .put("p_product", productId)
                    .put("p_quantity", 1)
                    .put("p_note", note.trim())
            )
        }
        return scalarId(raw) ?: currentBillId ?: error("Server ยังไม่ยืนยันบิล")
    }

    suspend fun reduceProduct(
        auth: NativeAuth,
        billId: String,
        type: String,
        tableId: String?,
        productId: String,
        note: String?
    ) {
        http.rpc(
            "pos_edit_bill",
            auth.session.accessToken,
            JSONObject()
                .put("p_order", billId)
                .put("p_type", type)
                .put("p_table", tableId ?: JSONObject.NULL)
                .put("p_product", productId)
                .put("p_quantity", -1)
                .put("p_note", note ?: "")
        )
    }

    suspend fun billAction(auth: NativeAuth, billId: String, action: String) {
        http.rpc(
            "pos_bill_action",
            auth.session.accessToken,
            JSONObject()
                .put("p_order", billId)
                .put("p_action", action)
                .put("p_method", JSONObject.NULL)
        )
    }

    suspend fun takeCash(auth: NativeAuth, billId: String, received: Double): String {
        val raw = http.rpc(
            "pos_take_payment",
            auth.session.accessToken,
            JSONObject()
                .put("p_order", billId)
                .put("p_method", "cash")
                .put("p_cash_received", received)
        )
        return scalarId(raw) ?: billId
    }

    private fun scalarId(raw: Any): String? = when (raw) {
        is String -> raw.takeIf { it.isNotBlank() && it != "null" }
        is JSONObject -> raw.optString("id").ifBlank {
            raw.optString("pos_create_bill_once").ifBlank { raw.optString("pos_edit_bill") }
        }.takeIf { it.isNotBlank() }
        is JSONArray -> {
            val first = raw.opt(0)
            when (first) {
                is String -> first
                is JSONObject -> first.optString("id").ifBlank { first.optString("pos_create_bill_once") }
                else -> first?.toString()
            }?.takeIf { it.isNotBlank() && it != "null" }
        }
        else -> raw.toString().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun orderNumber(raw: String?, id: String): String {
        val clean = raw.orEmpty().trim()
        if (clean.startsWith("QT-", true)) return clean.uppercase()
        val digits = clean.filter(Char::isDigit).takeLast(4)
        if (digits.length == 4) return "QT-" + digits
        return "QT-" + kotlin.math.abs(id.hashCode() % 10000).toString().padStart(4, '0')
    }
}
