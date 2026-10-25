package com.queuego.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun QgPlaceSearchPanel(
    enabled: Boolean = true,
    search: suspend (String) -> List<QueueGoPlaceSearchResult>,
    onSelect: (QueueGoPlaceSearchResult) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<QueueGoPlaceSearchResult>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query, enabled) {
        val normalized = query.trim()
        if (!enabled || normalized.length < 2) {
            results = emptyList()
            searched = false
            busy = false
            return@LaunchedEffect
        }
        delay(300)
        busy = true
        searched = false
        try {
            results = search(normalized).take(8)
            searched = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            results = emptyList()
            searched = true
        } finally {
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFFF8F9FA), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text("ค้นหาสถานที่ ถนน ตำบล อำเภอ จังหวัด") },
            trailingIcon = {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        )
        if (query.trim().length >= 2 && !busy) {
            if (results.isEmpty() && searched) {
                Text(
                    "ไม่พบสถานที่จาก QueueGo Places",
                    color = QgMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    results.forEach { result ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    query = result.name
                                    results = emptyList()
                                    searched = false
                                    onSelect(result)
                                }
                                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(result.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val detail = result.address ?: result.province
                                if (!detail.isNullOrBlank()) {
                                    Text(detail, color = QgMuted, fontSize = 10.sp, lineHeight = 13.sp)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("เลือก", color = QgRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
