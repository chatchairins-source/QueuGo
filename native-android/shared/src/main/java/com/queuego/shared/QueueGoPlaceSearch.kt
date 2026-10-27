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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun QgPlaceSearchPanel(
    enabled: Boolean = true,
    search: suspend (String) -> List<QueueGoPlaceSearchResult>,
    onSelect: (QueueGoPlaceSearchResult) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { QueueGoPlaceSearchCache(context) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<QueueGoPlaceSearchResult>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var selectedQuery by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query, enabled, selectedQuery) {
        val normalized = query.trim()
        if (selectedQuery == query) {
            results = emptyList()
            searched = false
            busy = false
            return@LaunchedEffect
        }
        if (!enabled || normalized.length < 2) {
            results = emptyList()
            searched = false
            busy = false
            return@LaunchedEffect
        }
        results = emptyList()
        searched = false
        busy = true
        delay(300)
        val cached = withContext(Dispatchers.IO) {
            runCatching { cache.read(normalized) }.getOrNull()
        }
        if (cached != null) {
            results = cached.results.take(8)
            searched = true
            if (cached.fresh) {
                busy = false
                return@LaunchedEffect
            }
        } else {
            searched = false
        }

        busy = true
        try {
            val fresh = search(normalized).take(8)
            if (fresh.isNotEmpty()) {
                results = fresh
                searched = true
                withContext(Dispatchers.IO) {
                    runCatching { cache.write(normalized, fresh) }
                }
            } else if (cached == null) {
                results = emptyList()
                searched = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (cached == null) {
                results = emptyList()
                searched = true
            }
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
            onValueChange = {
                selectedQuery = null
                query = it.take(120)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text("ค้นหาสถานที่ ถนน ตำบล อำเภอ จังหวัด") },
        )
        if (query.trim().length >= 2) {
            if (results.isEmpty() && searched && !busy) {
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
                                    selectedQuery = result.name
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
