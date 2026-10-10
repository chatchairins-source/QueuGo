package com.queuego.customer

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

data class CustomerMenuOption(
    val key: String,
    val name: String,
    val priceDelta: Double
)

data class CustomerMenuOptionGroup(
    val key: String,
    val name: String,
    val type: String,
    val required: Boolean,
    val options: List<CustomerMenuOption>
)

data class CustomerMenuSelection(
    val groupKey: String,
    val optionKey: String
)

data class CustomerResolvedMenuOption(
    val groupKey: String,
    val groupName: String,
    val optionKey: String,
    val optionName: String,
    val priceDelta: Double
)

internal fun customerVariantsJson(raw: Any?): String = when (raw) {
    null, JSONObject.NULL -> "[]"
    is JSONArray -> raw.toString()
    is String -> runCatching { JSONArray(raw).toString() }.getOrDefault("[]")
    else -> "[]"
}

internal fun customerSelectedOptionsLabel(raw: Any?): String? {
    val rows = when (raw) {
        null, JSONObject.NULL -> JSONArray()
        is JSONArray -> raw
        is String -> runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        else -> JSONArray()
    }
    val names = buildList {
        for (i in 0 until rows.length()) {
            val item = rows.optJSONObject(i) ?: continue
            item.optString("option_name").trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
    return names.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun customerMenuOptionGroups(rawJson: String): List<CustomerMenuOptionGroup> {
    val rows = runCatching { JSONArray(rawJson.ifBlank { "[]" }) }.getOrElse { JSONArray() }
    return buildList {
        for (i in 0 until rows.length()) {
            val group = rows.optJSONObject(i) ?: continue
            val options = group.optJSONArray("options") ?: continue
            val key = group.optString("key").trim()
            if (key.isBlank()) continue
            val type = group.optString("type", "multi").trim().lowercase()
            if (type !in setOf("single", "multi")) continue
            val parsedOptions = buildList {
                for (j in 0 until options.length()) {
                    val option = options.optJSONObject(j) ?: continue
                    val optionKey = option.optString("key").trim()
                    val optionName = option.optString("name").trim().ifBlank { optionKey }
                    val delta = option.optDouble("price_delta", 0.0)
                    if (
                        optionKey.isNotBlank() &&
                        delta.isFinite() &&
                        delta >= 0.0 &&
                        delta <= 999999.0
                    ) add(CustomerMenuOption(optionKey, optionName, roundCustomerMenuMoney(delta)))
                }
            }
            if (parsedOptions.isNotEmpty()) {
                add(
                    CustomerMenuOptionGroup(
                        key = key,
                        name = group.optString("name").trim().ifBlank { key },
                        type = type,
                        required = group.optBoolean("required", false),
                        options = parsedOptions
                    )
                )
            }
        }
    }
}

internal fun customerDefaultMenuSelections(product: CustomerProduct): List<CustomerMenuSelection> =
    customerMenuOptionGroups(product.variantsJson).mapNotNull { group ->
        if (group.required && group.key == "portion") {
            group.options.firstOrNull { it.key == "normal" }?.let {
                CustomerMenuSelection(group.key, it.key)
            }
        } else null
    }

internal fun customerResolveMenuSelections(
    product: CustomerProduct,
    rawSelections: List<CustomerMenuSelection>
): List<CustomerResolvedMenuOption> {
    val groups = customerMenuOptionGroups(product.variantsJson)
    val seen = linkedSetOf<Pair<String, String>>()
    rawSelections.forEach { selection ->
        require(selection.groupKey.isNotBlank() && selection.optionKey.isNotBlank()) {
            "ตัวเลือกเมนูไม่ถูกต้อง"
        }
        require(seen.add(selection.groupKey to selection.optionKey)) {
            "มีตัวเลือกเมนูซ้ำ"
        }
    }

    val resolved = mutableListOf<CustomerResolvedMenuOption>()
    var matchedExplicit = 0

    groups.forEach { group ->
        val explicit = rawSelections.filter { it.groupKey == group.key }
        require(group.type != "single" || explicit.size <= 1) {
            "เลือกได้เพียงหนึ่งตัวเลือกใน ${group.name}"
        }

        val effective = when {
            explicit.isNotEmpty() -> explicit
            group.required && group.key == "portion" -> {
                val normal = group.options.firstOrNull { it.key == "normal" }
                    ?: error("ไม่มีตัวเลือกธรรมดาสำหรับ ${group.name}")
                listOf(CustomerMenuSelection(group.key, normal.key))
            }
            group.required -> error("กรุณาเลือก ${group.name}")
            else -> emptyList()
        }

        effective.forEach { selection ->
            val option = group.options.firstOrNull { it.key == selection.optionKey }
                ?: error("ตัวเลือก ${selection.optionKey} ใช้งานไม่ได้")
            if (selection in explicit) matchedExplicit += 1
            resolved += CustomerResolvedMenuOption(
                groupKey = group.key,
                groupName = group.name,
                optionKey = option.key,
                optionName = option.name,
                priceDelta = option.priceDelta
            )
        }
    }

    require(matchedExplicit == rawSelections.size) { "มีตัวเลือกเมนูที่ไม่รู้จัก" }
    return resolved
}

internal fun customerCanonicalMenuSelections(
    product: CustomerProduct,
    rawSelections: List<CustomerMenuSelection>
): List<CustomerMenuSelection> =
    customerResolveMenuSelections(product, rawSelections)
        .map { CustomerMenuSelection(it.groupKey, it.optionKey) }

internal fun customerMenuSelectionPayload(selections: List<CustomerMenuSelection>): JSONArray =
    JSONArray().also { rows ->
        selections.forEach { selection ->
            rows.put(
                JSONObject()
                    .put("group_key", selection.groupKey)
                    .put("option_key", selection.optionKey)
            )
        }
    }

internal fun customerCartLineKey(line: CartLine): String =
    customerCartLineKey(line.product, line.selections)

internal fun customerCartLineKey(
    product: CustomerProduct,
    selections: List<CustomerMenuSelection>
): String {
    val canonical = customerCanonicalMenuSelections(product, selections)
        .joinToString("|") { it.groupKey + "=" + it.optionKey }
    return product.id + "|" + canonical
}

internal fun customerCartLineUnitPrice(line: CartLine): Double {
    val delta = customerResolveMenuSelections(line.product, line.selections).sumOf { it.priceDelta }
    return roundCustomerMenuMoney(line.product.deliveryPrice + delta)
}

internal fun customerCartLineOptionsLabel(line: CartLine): String =
    customerResolveMenuSelections(line.product, line.selections)
        .joinToString(" · ") { it.optionName }

private fun roundCustomerMenuMoney(value: Double): Double =
    round(value * 100.0) / 100.0
