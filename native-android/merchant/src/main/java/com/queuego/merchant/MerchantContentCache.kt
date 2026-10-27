package com.queuego.merchant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Durable local-first cache for Merchant content that is safe to show stale while
 * the authoritative server refresh runs in the background.
 *
 * Live order state, balances and dispatch state are intentionally not cached here.
 */
internal data class CachedMerchantContent(
    val shop: MerchantShop,
    val products: List<MerchantProduct>
)

internal class MerchantContentCache(context: Context) {
    companion object {
        private const val MAX_FILE_BYTES = 24L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES = 72L * 1024L * 1024L
    }

    private val root = File(context.noBackupFilesDir, "merchant_content_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(ownerId: String): CachedMerchantContent? {
        if (ownerId.isBlank()) return null
        val file = fileFor(ownerId)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return null
        return runCatching {
            val shop = json.optJSONObject("shop")?.toMerchantShop() ?: return@runCatching null
            val products = json.optJSONArray("products").toMerchantProducts()
            file.setLastModified(System.currentTimeMillis())
            CachedMerchantContent(shop, products)
        }.getOrNull()
    }

    fun write(ownerId: String, shop: MerchantShop, products: List<MerchantProduct>) {
        if (ownerId.isBlank()) return
        val json = JSONObject()
            .put("shop", shop.toJson())
            .put("products", JSONArray().also { array -> products.forEach { array.put(it.toJson()) } })
        writeJson(fileFor(ownerId), json)
    }

    private fun fileFor(ownerId: String): File =
        File(root, sha256(ownerId) + ".json")

    private fun writeJson(target: File, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val temp = File(root, target.name + ".tmp")
        runCatching {
            temp.outputStream().use { it.write(bytes) }
            if (!temp.renameTo(target)) {
                target.outputStream().use { it.write(bytes) }
                temp.delete()
            }
            target.setLastModified(System.currentTimeMillis())
            prune()
        }.onFailure { runCatching { temp.delete() } }
    }

    private fun prune() {
        val files = root.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length().coerceAtLeast(0L) }
        if (total <= MAX_TOTAL_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_TOTAL_BYTES * 3L / 4L) break
            val size = file.length().coerceAtLeast(0L)
            if (runCatching { file.delete() }.getOrDefault(false)) total -= size
        }
    }
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

private fun MerchantShop.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("category", category)
    .putNullable("logo", logo)
    .putNullable("cover", cover)
    .putNullable("address", address)
    .put("status", status)
    .put("onboardingStatus", onboardingStatus)
    .put("deliveryEnabled", deliveryEnabled)
    .putNullable("phone", phone)
    .putNullable("latitude", latitude)
    .putNullable("longitude", longitude)
    .putNullable("openTime", openTime)
    .putNullable("closeTime", closeTime)
    .putNullable("description", description)
    .put("shoppingSubcategories", JSONArray(shoppingSubcategories.toList()))
    .putNullable("riderPickupInstructions", riderPickupInstructions)
    .putNullable("riderParkingInstructions", riderParkingInstructions)

private fun JSONObject.toMerchantShop(): MerchantShop {
    val id = optString("id")
    require(id.isNotBlank())
    val subcategories = optJSONArray("shoppingSubcategories")?.let { array ->
        buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }.orEmpty()
    return MerchantShop(
        id = id,
        name = optString("name").ifBlank { "ร้านค้า" },
        category = optString("category").ifBlank { "other" },
        logo = optNullableString("logo"),
        cover = optNullableString("cover"),
        address = optNullableString("address"),
        status = optString("status"),
        onboardingStatus = optString("onboardingStatus"),
        deliveryEnabled = optBoolean("deliveryEnabled", false),
        phone = optNullableString("phone"),
        latitude = optNullableDouble("latitude"),
        longitude = optNullableDouble("longitude"),
        openTime = optNullableString("openTime"),
        closeTime = optNullableString("closeTime"),
        description = optNullableString("description"),
        shoppingSubcategories = subcategories,
        riderPickupInstructions = optNullableString("riderPickupInstructions"),
        riderParkingInstructions = optNullableString("riderParkingInstructions")
    )
}

private fun MerchantProduct.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .putNullable("description", description)
    .put("price", price)
    .put("deliveryPrice", deliveryPrice)
    .putNullable("image", image)
    .put("available", available)
    .put("category", category)
    .put("stock", stock)
    .put("variantsJson", variantsJson)
    .put("posAvailable", posAvailable)
    .put("deliveryAvailable", deliveryAvailable)
    .putNullable("posPrice", posPrice)
    .put("deliveryRestriction", deliveryRestriction)
    .putNullable("catalogPreviewImage", catalogPreviewImage)
    .put("inventoryStockTracking", inventoryStockTracking)
    .putNullable("groceryMasterId", groceryMasterId)
    .put("groceryStockTracking", groceryStockTracking)

private fun JSONArray?.toMerchantProducts(): List<MerchantProduct> = buildList {
    val source = this@toMerchantProducts ?: return@buildList
    for (index in 0 until source.length()) {
        val row = source.optJSONObject(index) ?: continue
        val id = row.optString("id")
        if (id.isBlank()) continue
        add(
            MerchantProduct(
                id = id,
                name = row.optString("name").ifBlank { "สินค้า" },
                description = row.optNullableString("description"),
                price = row.optDouble("price", 0.0),
                deliveryPrice = row.optDouble("deliveryPrice", row.optDouble("price", 0.0)),
                image = row.optNullableString("image"),
                available = row.optBoolean("available", true),
                category = row.optString("category"),
                stock = row.optInt("stock", 0),
                variantsJson = row.optString("variantsJson").ifBlank { "[]" },
                posAvailable = row.optBoolean("posAvailable", true),
                deliveryAvailable = row.optBoolean("deliveryAvailable", true),
                posPrice = row.optNullableDouble("posPrice"),
                deliveryRestriction = row.optString("deliveryRestriction").ifBlank { "none" },
                catalogPreviewImage = row.optNullableString("catalogPreviewImage"),
                inventoryStockTracking = row.optBoolean("inventoryStockTracking", false),
                groceryMasterId = row.optNullableString("groceryMasterId"),
                groceryStockTracking = row.optBoolean("groceryStockTracking", false)
            )
        )
    }
}
