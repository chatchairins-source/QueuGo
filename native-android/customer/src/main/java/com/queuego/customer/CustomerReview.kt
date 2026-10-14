package com.queuego.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class CustomerReview(
    val id: String,
    val rating: Int,
    val foodRating: Int?,
    val riderRating: Int?,
    val comment: String?
)

internal val customerRiderComplimentOptions = listOf(
    "polite" to "สุภาพ",
    "fast" to "ส่งไว",
    "careful" to "ดูแลสินค้า",
    "professional" to "เป็นมืออาชีพ",
    "friendly" to "เป็นมิตร",
    "helpful" to "ช่วยเหลือดี"
)

internal fun customerRiderComplimentsFromRpc(raw: Any?): Set<String> {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    val values = row.optJSONArray("compliments") ?: JSONArray()
    val allowed = customerRiderComplimentOptions.mapTo(linkedSetOf()) { it.first }
    return buildSet {
        for (index in 0 until values.length()) {
            val key = values.optString(index).trim().lowercase()
            if (key in allowed) add(key)
        }
    }
}

internal fun customerRiderComplimentLabel(key: String): String =
    customerRiderComplimentOptions.firstOrNull { it.first == key }?.second ?: key

class CustomerReviewApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun load(auth: NativeAuth, orderId: String): CustomerReview? {
        val rows = http.array(
            http.get(
                "reviews?select=id,rating,food_rating,rider_rating,comment" +
                    "&order_id=eq." + http.enc(orderId) +
                    "&customer_id=eq." + http.enc(auth.user.id) +
                    "&limit=1",
                auth.session.accessToken
            )
        )
        val r = rows.optJSONObject(0) ?: return null
        return CustomerReview(
            id = r.optString("id"),
            rating = r.optInt("rating", 0),
            foodRating = r.optInt("food_rating").takeIf { !r.isNull("food_rating") && it > 0 },
            riderRating = r.optInt("rider_rating").takeIf { !r.isNull("rider_rating") && it > 0 },
            comment = r.optString("comment").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    suspend fun save(
        auth: NativeAuth,
        orderId: String,
        rating: Int,
        foodRating: Int?,
        riderRating: Int?,
        comment: String?
    ) {
        require(rating in 1..5) { "กรุณาเลือกคะแนนร้าน 1–5 ดาว" }
        http.rpc(
            "qg_customer_save_review",
            auth.session.accessToken,
            JSONObject()
                .put("p_order_id", orderId)
                .put("p_rating", rating)
                .put("p_food_rating", foodRating ?: JSONObject.NULL)
                .put("p_rider_rating", riderRating ?: JSONObject.NULL)
                .put("p_comment", comment?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        )
    }

    suspend fun loadRiderCompliments(auth: NativeAuth, orderId: String): Set<String> {
        val raw = http.rpc(
            "qg_customer_rider_compliments",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId)
        )
        return customerRiderComplimentsFromRpc(raw)
    }

    suspend fun saveRiderCompliments(
        auth: NativeAuth,
        orderId: String,
        compliments: Set<String>
    ) {
        val normalized = customerRiderComplimentOptions.map { it.first }
            .filter { it in compliments }
            .take(3)
        http.rpc(
            "qg_customer_save_rider_compliments",
            auth.session.accessToken,
            JSONObject()
                .put("p_order_id", orderId)
                .put("p_compliments", JSONArray(normalized))
        )
    }

    suspend fun delete(auth: NativeAuth, reviewId: String) {
        val raw = http.delete(
            "reviews?id=eq." + http.enc(reviewId) +
                "&customer_id=eq." + http.enc(auth.user.id),
            auth.session.accessToken
        )
        if (raw is JSONArray && raw.length() == 0) {
            val verify = http.array(
                http.get(
                    "reviews?select=id&id=eq." + http.enc(reviewId) +
                        "&customer_id=eq." + http.enc(auth.user.id),
                    auth.session.accessToken
                )
            )
            if (verify.length() > 0) error("ระบบยังไม่ยืนยันการลบรีวิว")
        }
    }
}

@Composable
fun CustomerReviewCard(
    auth: NativeAuth,
    order: CustomerOrder,
    shopCategory: String?
) {
    val api = remember { CustomerReviewApi() }
    val scope = rememberCoroutineScope()
    var review by remember(order.id) { mutableStateOf<CustomerReview?>(null) }
    var loaded by remember(order.id) { mutableStateOf(false) }
    var editing by remember(order.id) { mutableStateOf(false) }
    var rating by remember(order.id) { mutableStateOf(0) }
    var foodRating by remember(order.id) { mutableStateOf(0) }
    var riderRating by remember(order.id) { mutableStateOf(0) }
    var riderCompliments by remember(order.id) { mutableStateOf<Set<String>>(emptySet()) }
    var storedRiderCompliments by remember(order.id) { mutableStateOf<Set<String>>(emptySet()) }
    var comment by remember(order.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteArmed by remember { mutableStateOf(false) }

    fun hydrate(value: CustomerReview?) {
        review = value
        rating = value?.rating ?: 0
        foodRating = value?.foodRating ?: 0
        riderRating = value?.riderRating ?: 0
        comment = value?.comment.orEmpty()
        editing = value == null
        deleteArmed = false
    }

    LaunchedEffect(order.id) {
        runCatching {
            val loadedReview = api.load(auth, order.id)
            val compliments = if (loadedReview != null) {
                api.loadRiderCompliments(auth, order.id)
            } else {
                emptySet()
            }
            loadedReview to compliments
        }.onSuccess { (loadedReview, compliments) ->
            hydrate(loadedReview)
            storedRiderCompliments = compliments
            riderCompliments = compliments
        }.onFailure { message = it.message ?: "โหลดรีวิวไม่สำเร็จ" }
        loaded = true
    }

    QgCard(Modifier.fillMaxWidth()) {
        Column {
            Text("รีวิวของคุณ", fontWeight = FontWeight.ExtraBold)
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(message!!, color = QgRed)
            }
            if (!loaded) {
                Spacer(Modifier.height(6.dp))
                Text("กำลังโหลดรีวิว...", color = QgMuted)
                return@Column
            }

            val current = review
            if (current != null && !editing) {
                Spacer(Modifier.height(8.dp))
                ReviewReadRow("คะแนนร้าน", current.rating)
                if (shopCategory in setOf("food", "cafe") && current.foodRating != null) {
                    ReviewReadRow("คะแนนอาหาร", current.foodRating)
                }
                if (current.riderRating != null) {
                    ReviewReadRow("คะแนน Rider", current.riderRating)
                }
                if (storedRiderCompliments.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "คำชม Rider · " + customerRiderComplimentOptions
                            .filter { it.first in storedRiderCompliments }
                            .joinToString(" · ") { it.second },
                        color = QgMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!current.comment.isNullOrBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(current.comment!!)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            riderCompliments = storedRiderCompliments
                            editing = true
                            deleteArmed = false
                            message = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("แก้ไขรีวิว") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(
                        onClick = {
                            if (!deleteArmed) {
                                deleteArmed = true
                                message = "กดลบอีกครั้งเพื่อยืนยัน"
                            } else if (!busy) {
                                busy = true
                                scope.launch {
                                    runCatching { api.delete(auth, current.id) }
                                        .onSuccess {
                                            storedRiderCompliments = emptySet()
                                            riderCompliments = emptySet()
                                            hydrate(null)
                                            message = "ลบรีวิวแล้ว"
                                        }
                                        .onFailure { message = it.message ?: "ลบรีวิวไม่สำเร็จ" }
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (deleteArmed) "ยืนยันลบ" else "ลบรีวิว") }
                }
            } else if (editing) {
                Spacer(Modifier.height(8.dp))
                RatingPicker("คะแนนร้าน", rating) { rating = it }
                if (shopCategory in setOf("food", "cafe")) {
                    Spacer(Modifier.height(8.dp))
                    RatingPicker("คะแนนอาหาร (ไม่บังคับ)", foodRating) { foodRating = it }
                }
                Spacer(Modifier.height(8.dp))
                RatingPicker("คะแนน Rider (ไม่บังคับ)", riderRating) {
                    riderRating = it
                    if (it < 4) riderCompliments = emptySet()
                }
                if (riderRating in 4..5) {
                    Spacer(Modifier.height(8.dp))
                    RiderComplimentPicker(
                        selected = riderCompliments,
                        onToggle = { key ->
                            riderCompliments = if (key in riderCompliments) {
                                riderCompliments - key
                            } else if (riderCompliments.size < 3) {
                                riderCompliments + key
                            } else {
                                message = "เลือกคำชม Rider ได้สูงสุด 3 ข้อ"
                                riderCompliments
                            }
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 1000) comment = it },
                    label = { Text("ความคิดเห็น") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )
                Spacer(Modifier.height(9.dp))
                Button(
                    onClick = {
                        if (busy || rating !in 1..5) return@Button
                        busy = true
                        message = null
                        scope.launch {
                            runCatching {
                                api.save(
                                    auth = auth,
                                    orderId = order.id,
                                    rating = rating,
                                    foodRating = foodRating.takeIf { it in 1..5 },
                                    riderRating = riderRating.takeIf { it in 1..5 },
                                    comment = comment
                                )
                                if (riderRating in 4..5) {
                                    api.saveRiderCompliments(auth, order.id, riderCompliments)
                                }
                            }.onSuccess {
                                runCatching {
                                    val refreshed = api.load(auth, order.id)
                                    val compliments = if ((refreshed?.riderRating ?: 0) in 4..5) {
                                        api.loadRiderCompliments(auth, order.id)
                                    } else {
                                        emptySet()
                                    }
                                    refreshed to compliments
                                }.onSuccess { (refreshed, compliments) ->
                                    hydrate(refreshed)
                                    storedRiderCompliments = compliments
                                    riderCompliments = compliments
                                }
                                message = "บันทึกรีวิวแล้ว"
                            }.onFailure { message = it.message ?: "บันทึกรีวิวไม่สำเร็จ" }
                            busy = false
                        }
                    },
                    enabled = !busy && rating in 1..5,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "กำลังบันทึก..." else "บันทึกรีวิว") }
                if (current != null) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            riderCompliments = storedRiderCompliments
                            hydrate(current)
                            editing = false
                            message = null
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ยกเลิกการแก้ไข") }
                }
            }
        }
    }
}

@Composable
private fun RiderComplimentPicker(
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Column {
        Text("คำชม Rider (เลือกได้สูงสุด 3 ข้อ)", color = QgMuted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        customerRiderComplimentOptions.chunked(2).forEach { options ->
            Row(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (key, label) ->
                    if (key in selected) {
                        Button(onClick = { onToggle(key) }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = { onToggle(key) }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    }
                    if (index == 0) Spacer(Modifier.padding(3.dp))
                }
                if (options.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun ReviewReadRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = QgMuted)
        Text(
            (1..5).joinToString("") { if (it <= value) "★" else "☆" },
            color = QgRed,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RatingPicker(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, color = QgMuted, fontWeight = FontWeight.Bold)
        Row {
            (1..5).forEach { star ->
                Text(
                    if (star <= value) "★" else "☆",
                    color = if (star <= value) QgRed else QgMuted,
                    modifier = Modifier
                        .clickable { onChange(star) }
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}
