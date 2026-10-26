package com.queuego.merchant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgRed
import org.json.JSONArray
import org.json.JSONObject

data class PosProductOption(val key: String, val name: String, val priceDelta: Double)
data class PosProductOptionGroup(
    val key: String, val name: String, val type: String, val min: Int, val max: Int,
    val options: List<PosProductOption>
)

internal fun parsePosProductOptionGroups(raw: JSONArray): List<PosProductOptionGroup> {
    val structured = buildList {
        for (i in 0 until raw.length()) {
            val group = raw.optJSONObject(i) ?: continue
            if (group.optString("schema") != "queuego.menu-options.v1") continue
            val key = group.optString("key").trim()
            val type = group.optString("type")
            val rawOptions = group.optJSONArray("options") ?: continue
            if (key.isBlank() || type !in setOf("single", "multi")) continue
            val options = buildList {
                for (j in 0 until rawOptions.length()) {
                    val option = rawOptions.optJSONObject(j) ?: continue
                    val optionKey = option.optString("key").trim()
                    if (optionKey.isBlank()) continue
                    val delta = option.optDouble("price_delta", option.optDouble("price", 0.0))
                    if (!delta.isFinite() || delta < 0.0) continue
                    add(PosProductOption(optionKey, option.optString("name").ifBlank { optionKey }, delta))
                }
            }
            val required = group.optBoolean("required", false)
            val minRaw = group.optInt("min", -1)
            val maxRaw = group.optInt("max", -1)
            val min = if (type == "single") if (required) 1 else 0 else
                if (minRaw >= 0) minRaw else if (required) 1 else 0
            val max = if (type == "single") 1 else if (maxRaw >= 0) maxRaw.coerceAtMost(options.size) else options.size
            add(PosProductOptionGroup(key, group.optString("name").ifBlank { "ตัวเลือกสินค้า" }, type, min, max, options))
        }
    }
    if (structured.isNotEmpty()) return structured
    val legacy = buildList {
        for (i in 0 until raw.length()) {
            val name = (raw.opt(i) as? String)?.trim().orEmpty()
            if (name.isNotBlank()) add(PosProductOption(name, name, 0.0))
        }
    }
    return if (legacy.isEmpty()) emptyList() else listOf(
        PosProductOptionGroup("variant", "ตัวเลือกสินค้า", "single", 1, 1, legacy)
    )
}

internal fun defaultPosProductOptionSelections(groups: List<PosProductOptionGroup>): Map<String, List<String>> =
    groups.associate { group ->
        group.key to if (group.type == "single" && group.min > 0) group.options.firstOrNull()?.let { listOf(it.key) }.orEmpty() else emptyList()
    }

internal fun posProductOptionPayload(
    groups: List<PosProductOptionGroup>,
    selections: Map<String, List<String>>
): JSONObject {
    require(groups.isNotEmpty()) { "ไม่มีตัวเลือกสินค้าสำหรับรายการนี้" }
    val allowedKeys = groups.map { it.key }.toSet()
    require(selections.keys.all { it in allowedKeys }) { "ตัวเลือกสินค้าไม่ถูกต้อง" }
    val payload = JSONObject()
    for (group in groups) {
        val selected = selections[group.key].orEmpty()
        require(selected.size >= group.min) { "กรุณาเลือกอย่างน้อย ${group.min} รายการใน ${group.name}" }
        require(selected.size <= group.max) { "เลือกได้ไม่เกิน ${group.max} รายการใน ${group.name}" }
        require(selected.distinct().size == selected.size) { "มีตัวเลือกสินค้าซ้ำ" }
        require(selected.all { key -> group.options.any { it.key == key } }) { "ตัวเลือกสินค้าไม่ถูกต้อง" }
        if (group.type == "single") {
            if (selected.isNotEmpty()) payload.put(group.key, selected.single())
        } else {
            payload.put(group.key, JSONArray(group.options.map { it.key }.filter { it in selected }))
        }
    }
    return JSONObject(canonicalPosOptionJson(payload.toString()))
}

internal fun canonicalPosOptionJson(raw: String): String {
    val input = JSONObject(raw)
    val out = JSONObject()
    input.keys().asSequence().toList().sorted().forEach { key -> out.put(key, input.get(key)) }
    return out.toString()
}

internal fun posProductOptionGroupsJson(groups: List<PosProductOptionGroup>): JSONArray = JSONArray().apply {
    groups.forEach { group ->
        put(JSONObject().put("schema", "queuego.menu-options.v1").put("key", group.key)
            .put("name", group.name).put("type", group.type).put("required", group.min > 0)
            .put("min", group.min).put("max", group.max)
            .put("options", JSONArray().apply { group.options.forEach { option ->
                put(JSONObject().put("key", option.key).put("name", option.name).put("price_delta", option.priceDelta))
            } }))
    }
}

@Composable
internal fun MerchantPosOptionSelectionDialog(
    product: PosProduct,
    groups: List<PosProductOptionGroup>,
    onDismiss: () -> Unit,
    onConfirm: (JSONObject) -> Unit
) {
    var choices by remember(product.id) { mutableStateOf(defaultPosProductOptionSelections(groups)) }
    var validationMessage by remember(product.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("เลือกตัวเลือก · ${product.name}") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                groups.forEach { group ->
                    Text(group.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    group.options.forEach { option ->
                        val selected = option.key in choices[group.key].orEmpty()
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                choices = choices.toMutableMap().apply {
                                    val current = this[group.key].orEmpty()
                                    this[group.key] = if (group.type == "single") listOf(option.key)
                                        else if (selected) current - option.key else current + option.key
                                }
                                validationMessage = null
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (group.type == "single") RadioButton(selected, onClick = {
                                choices = choices.toMutableMap().apply { this[group.key] = listOf(option.key) }
                                validationMessage = null
                            }) else Checkbox(selected, onCheckedChange = {
                                choices = choices.toMutableMap().apply {
                                    val current = this[group.key].orEmpty()
                                    this[group.key] = if (it) current + option.key else current - option.key
                                }
                                validationMessage = null
                            })
                            Text(option.name, Modifier.weight(1f))
                            Text(if (option.priceDelta > 0) "+ ${option.priceDelta}" else "ราคาเดิม")
                        }
                    }
                }
                validationMessage?.let { Text(it, color = QgRed, fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { posProductOptionPayload(groups, choices) }
                    .onSuccess(onConfirm)
                    .onFailure { validationMessage = it.message ?: "ตัวเลือกสินค้าไม่ถูกต้อง" }
            }) { Text("เพิ่มลงบิล") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } }
    )
}
