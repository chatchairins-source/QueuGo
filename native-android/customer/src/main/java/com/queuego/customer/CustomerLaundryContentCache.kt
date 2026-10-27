package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Durable local-first cache for the Customer laundry catalog.
 *
 * The catalog is safe to render stale while the authoritative server refresh runs in
 * the background. Checkout is still server-authoritative.
 */
internal class CustomerLaundryContentCache(context: Context) {
    companion object {
        private const val MAX_FILE_BYTES = 4L * 1024L * 1024L
    }

    private val root = File(context.noBackupFilesDir, "customer_laundry_cache_v1").also {
        runCatching { it.mkdirs() }
    }
    private val file = File(root, "catalog.json")

    fun read(): LaundryCatalog? {
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return null
        return runCatching { decode(json) }.getOrNull()
    }

    fun write(catalog: LaundryCatalog) {
        val json = JSONObject()
            .put("featureEnabled", catalog.featureEnabled)
            .put("hubs", JSONArray().also { rows ->
                catalog.hubs.forEach { hub ->
                    rows.put(
                        JSONObject()
                            .put("id", hub.id)
                            .put("name", hub.name)
                            .put("shopId", hub.shopId ?: JSONObject.NULL)
                    )
                }
            })
            .put("settings", JSONArray().also { rows ->
                catalog.settings.values.forEach { setting ->
                    rows.put(
                        JSONObject()
                            .put("hubId", setting.hubId)
                            .put("enabled", setting.enabled)
                            .put("minimumOrder", setting.minimumOrder)
                            .put("pickupFee", setting.pickupFee)
                            .put("returnFee", setting.returnFee)
                            .put("roundTripFee", setting.roundTripFee)
                            .put("deliveryFeeMode", setting.deliveryFeeMode)
                    )
                }
            })
            .put("services", JSONArray().also { rows ->
                catalog.servicesByHub.values.flatten().forEach { service ->
                    rows.put(
                        JSONObject()
                            .put("id", service.id)
                            .put("hubId", service.hubId)
                            .put("name", service.name)
                            .put("description", service.description ?: JSONObject.NULL)
                            .put("price", service.price)
                            .put("pricingType", service.pricingType)
                            .put("estimatedMinutes", service.estimatedMinutes ?: JSONObject.NULL)
                    )
                }
            })

        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val temp = File(root, "catalog.tmp")
        runCatching {
            temp.outputStream().use { it.write(bytes) }
            if (!temp.renameTo(file)) {
                file.outputStream().use { it.write(bytes) }
                temp.delete()
            }
            file.setLastModified(System.currentTimeMillis())
        }.onFailure { runCatching { temp.delete() } }
    }

    private fun decode(json: JSONObject): LaundryCatalog {
        val hubs = buildList {
            val rows = json.optJSONArray("hubs") ?: JSONArray()
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(
                    LaundryHub(
                        id = id,
                        name = row.optString("name").ifBlank { "ร้านฝากซัก" },
                        shopId = row.optString("shopId")
                            .takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }

        val hubIds = hubs.mapTo(linkedSetOf()) { it.id }
        val settings = linkedMapOf<String, LaundrySettings>()
        val settingRows = json.optJSONArray("settings") ?: JSONArray()
        for (index in 0 until settingRows.length()) {
            val row = settingRows.optJSONObject(index) ?: continue
            val hubId = row.optString("hubId")
            if (hubId !in hubIds) continue
            val values = listOf(
                row.optDouble("minimumOrder", 0.0),
                row.optDouble("pickupFee", 0.0),
                row.optDouble("returnFee", 0.0),
                row.optDouble("roundTripFee", 0.0)
            )
            if (values.any { !it.isFinite() || it < 0.0 }) continue
            settings[hubId] = LaundrySettings(
                hubId = hubId,
                enabled = row.optBoolean("enabled", true),
                minimumOrder = values[0],
                pickupFee = values[1],
                returnFee = values[2],
                roundTripFee = values[3],
                deliveryFeeMode = row.optString("deliveryFeeMode").ifBlank { "separate" }
            )
        }

        val servicesByHub = linkedMapOf<String, MutableList<LaundryService>>()
        val serviceRows = json.optJSONArray("services") ?: JSONArray()
        for (index in 0 until serviceRows.length()) {
            val row = serviceRows.optJSONObject(index) ?: continue
            val id = row.optString("id")
            val hubId = row.optString("hubId")
            val price = row.optDouble("price", 0.0)
            val pricingType = row.optString("pricingType")
            if (
                id.isBlank() || hubId !in hubIds || !price.isFinite() || price < 0.0 ||
                pricingType !in setOf("per_kg", "per_item", "per_set", "fixed")
            ) continue
            servicesByHub.getOrPut(hubId) { mutableListOf() } += LaundryService(
                id = id,
                hubId = hubId,
                name = row.optString("name").ifBlank { "บริการฝากซัก" },
                description = row.optString("description").takeIf { it.isNotBlank() && it != "null" },
                price = price,
                pricingType = pricingType,
                estimatedMinutes = if (row.has("estimatedMinutes") && !row.isNull("estimatedMinutes")) {
                    row.optInt("estimatedMinutes")
                } else null
            )
        }

        val featureEnabled = json.optBoolean("featureEnabled", false)
        if (!featureEnabled) return LaundryCatalog(false, emptyList(), emptyMap())
        val validHubs = hubs.filter { hub ->
            settings.containsKey(hub.id) && !servicesByHub[hub.id].isNullOrEmpty()
        }
        return LaundryCatalog(
            featureEnabled = true,
            hubs = validHubs,
            settings = settings.filterKeys { id -> validHubs.any { it.id == id } },
            servicesByHub = servicesByHub
                .filterKeys { id -> validHubs.any { it.id == id } }
                .mapValues { it.value.toList() }
        )
    }
}
