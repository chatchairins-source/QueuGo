package com.queuego.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.CancellationException

internal data class CustomerPromotion(
    val shopId: String,
    val shopName: String,
    val title: String,
    val description: String
)

private class CustomerPromotionApi(
    private val http: QueueGoNativeApi = QueueGoNativeApi()
) {
    suspend fun publicPromotions(): List<CustomerPromotion> {
        val rows = http.array(http.rpc("qg_public_promotions", null))
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val shopId = row.optString("shop_id")
                if (shopId.isBlank()) continue
                add(
                    CustomerPromotion(
                        shopId = shopId,
                        shopName = row.optString("shop_name").ifBlank { "ร้านค้า" },
                        title = row.optString("title").ifBlank { "โปรโมชั่น" },
                        description = row.optString("description")
                    )
                )
            }
        }
    }
}

@Composable
internal fun CustomerPromotionScreen(
    onBack: () -> Unit,
    onOpenShop: (String) -> Unit
) {
    val api = remember { CustomerPromotionApi() }
    var loading by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<CustomerPromotion>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            rows = api.publicPromotions()
            error = null
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message ?: "โหลดโปรโมชั่นไม่สำเร็จ"
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text("โปรโมชั่นจากร้าน", fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))

        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = QgRed)
            rows.isEmpty() -> Text("ยังไม่มีโปรโมชั่นที่เปิดใช้งาน", color = QgMuted)
            else -> rows.forEach { promotion ->
                QgCard(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onOpenShop(promotion.shopId) }
                ) {
                    Column {
                        Text(promotion.shopName, color = QgRed, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(promotion.title, fontWeight = FontWeight.ExtraBold)
                        if (promotion.description.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(promotion.description, color = QgMuted)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
