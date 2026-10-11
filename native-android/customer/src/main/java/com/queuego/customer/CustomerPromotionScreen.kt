package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgIcon
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
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 7.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("back", Modifier.size(20.dp), Color(0xFF17191D))
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "โปรโมชั่นจากร้าน",
                color = Color(0xFF17191D),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            loading -> Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = QgRed)
            }

            error != null -> Text(
                error.orEmpty(),
                color = QgRed,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            rows.isEmpty() -> Text(
                "ยังไม่มีโปรโมชั่นที่เปิดใช้งาน",
                color = QgMuted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 35.dp)
            )

            else -> rows.forEach { promotion ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFECEEF1), RoundedCornerShape(16.dp))
                        .padding(13.dp)
                ) {
                    Text(
                        promotion.shopName,
                        color = QgRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onOpenShop(promotion.shopId) }
                            .padding(vertical = 4.dp)
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        promotion.title,
                        color = Color(0xFF17191D),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (promotion.description.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            promotion.description,
                            color = Color(0xFF55585E),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
