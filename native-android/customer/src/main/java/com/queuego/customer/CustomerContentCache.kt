package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Durable cache for public Customer browsing content.
 *
 * UI may render these rows immediately, but server refresh remains authoritative for
 * availability, price, stock, shop-open state and checkout validation.
 */
internal data class CachedCustomerHome(
    val shops: List<CustomerShop>,
    val banners: List<HomeBanner>,
    val serviceBanners: Map<String, ServiceBanner>
)

internal data class CachedCustomerMarket(
    val markets: List<MarketInfo>,
    val shops: List<MarketShop>,
    val catalog: List<MarketProduct>
)

internal class CustomerContentCache(context: Context) {
    companion object {
        private const val MAX_CACHE_FILE_BYTES = 24L * 1024L * 1024L
        private const val MAX_CACHE_TOTAL_BYTES = 96L * 1024L * 1024L
        private const val MAX_GROCERY_CATEGORIES = 200
        private const val MAX_GROCERY_CATEGORY_LENGTH = 100
    }

    private val root = File(context.noBackupFilesDir, "customer_content_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun readHome(): CachedCustomerHome? {
        val json = readJson(File(root, "home.json")) ?: return null
        return runCatching {
            val shops = json.optJSONArray("shops").toCustomerShops()
            val banners = json.optJSONArray("banners").toHomeBanners()
            val service = json.optJSONArray("serviceBanners")
                .toServiceBanners()
                .associateBy { it.key }
            CachedCustomerHome(shops, banners, service)
        }.getOrNull()
    }

    fun writeHome(
        shops: List<CustomerShop>,
        banners: List<HomeBanner>,
        serviceBanners: Map<String, ServiceBanner>
    ) {
        val json = JSONObject()
            .put("shops", JSONArray().also { array -> shops.forEach { array.put(it.toJson()) } })
            .put("banners", JSONArray().also { array -> banners.forEach { array.put(it.toJson()) } })
            .put(
                "serviceBanners",
                JSONArray().also { array -> serviceBanners.values.forEach { array.put(it.toJson()) } }
            )
        writeJson(File(root, "home.json"), json)
    }

    fun readProducts(shopId: String): List<CustomerProduct> {
        val json = readJson(productFile(shopId)) ?: return emptyList()
        return runCatching { json.optJSONArray("products").toCustomerProducts(shopId) }
            .getOrDefault(emptyList())
    }

    fun writeProducts(shopId: String, products: List<CustomerProduct>) {
        if (shopId.isBlank() || products.any { it.shopId != shopId }) return
        val json = JSONObject()
            .put("shopId", shopId)
            .put("products", JSONArray().also { array -> products.forEach { array.put(it.toJson()) } })
        writeJson(productFile(shopId), json)
    }

    fun readGroceryCategories(shopId: String): List<String> {
        if (shopId.isBlank()) return emptyList()
        val json = readJson(groceryCategoryFile(shopId)) ?: return emptyList()
        if (json.optString("shopId") != shopId) return emptyList()
        return runCatching {
            val array = json.optJSONArray("categories") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until minOf(array.length(), MAX_GROCERY_CATEGORIES)) {
                    val category = array.optString(index).trim()
                    if (category.isNotEmpty() && category.length <= MAX_GROCERY_CATEGORY_LENGTH && category !in this) {
                        add(category)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun writeGroceryCategories(shopId: String, categories: List<String>) {
        if (shopId.isBlank() || categories.size > MAX_GROCERY_CATEGORIES ||
            categories.any { it.isBlank() || it.trim().length > MAX_GROCERY_CATEGORY_LENGTH }) return
        val json = JSONObject()
            .put("shopId", shopId)
            .put("categories", JSONArray().also { array -> categories.distinct().forEach { array.put(it.trim()) } })
        writeJson(groceryCategoryFile(shopId), json)
    }

    fun readMarket(): CachedCustomerMarket? {
        val json = readJson(File(root, "market.json")) ?: return null
        return runCatching {
            CachedCustomerMarket(
                markets = json.optJSONArray("markets").toMarkets(),
                shops = json.optJSONArray("shops").toMarketShops(),
                catalog = json.optJSONArray("catalog").toMarketProducts()
            )
        }.getOrNull()
    }

    fun writeMarket(
        markets: List<MarketInfo>,
        shops: List<MarketShop>,
        catalog: List<MarketProduct>
    ) {
        val json = JSONObject()
            .put("markets", JSONArray().also { array -> markets.forEach { array.put(it.toJson()) } })
            .put("shops", JSONArray().also { array -> shops.forEach { array.put(it.toJson()) } })
            .put("catalog", JSONArray().also { array -> catalog.forEach { array.put(it.toJson()) } })
        writeJson(File(root, "market.json"), json)
    }

    fun readPromotions(): List<CustomerPromotion> {
        val json = readJson(File(root, "promotions.json")) ?: return emptyList()
        return runCatching { json.optJSONArray("promotions").toCustomerPromotions() }
            .getOrDefault(emptyList())
    }

    fun writePromotions(promotions: List<CustomerPromotion>) {
        val json = JSONObject()
            .put("promotions", JSONArray().also { array -> promotions.forEach { array.put(it.toJson()) } })
        writeJson(File(root, "promotions.json"), json)
    }

    private fun productFile(shopId: String): File =
        File(root, "products_" + sha256(shopId) + ".json")

    private fun groceryCategoryFile(shopId: String): File =
        File(root, "grocery_categories_" + sha256(shopId) + ".json")

    private fun readJson(file: File): JSONObject? {
        if (!file.isFile || file.length() !in 1L..MAX_CACHE_FILE_BYTES) return null
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull()
    }

    private fun writeJson(target: File, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_CACHE_FILE_BYTES) return
        val temp = File(root, target.name + ".tmp")
        runCatching {
            temp.outputStream().use { it.write(bytes) }
            if (!temp.renameTo(target)) {
                target.outputStream().use { it.write(bytes) }
                temp.delete()
            }
            target.setLastModified(System.currentTimeMillis())
            prune()
        }.onFailure {
            runCatching { temp.delete() }
        }
    }

    private fun prune() {
        val files = root.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length().coerceAtLeast(0L) }
        if (total <= MAX_CACHE_TOTAL_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_TOTAL_BYTES * 3L / 4L) break
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

private fun JSONArray?.objects(): Sequence<JSONObject> = sequence {
    val source = this@objects ?: return@sequence
    for (index in 0 until source.length()) {
        source.optJSONObject(index)?.let { yield(it) }
    }
}

private fun CustomerShop.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("category", category)
    .putNullable("logo", logo)
    .putNullable("cover", cover)
    .putNullable("address", address)
    .putNullable("latitude", latitude)
    .putNullable("longitude", longitude)
    .put("open", open)
    .putNullable("openTime", openTime)
    .putNullable("closeTime", closeTime)
    .put("subcategories", JSONArray(subcategories.toList()))
    .putNullable("description", description)

private fun JSONArray?.toCustomerShops(): List<CustomerShop> = objects().mapNotNull { row ->
    val id = row.optString("id")
    if (id.isBlank()) return@mapNotNull null
    CustomerShop(
        id = id,
        name = row.optString("name").ifBlank { "ร้านค้า" },
        category = row.optString("category").ifBlank { "other" },
        logo = row.optNullableString("logo"),
        cover = row.optNullableString("cover"),
        address = row.optNullableString("address"),
        latitude = row.optNullableDouble("latitude"),
        longitude = row.optNullableDouble("longitude"),
        open = row.optBoolean("open", true),
        openTime = row.optNullableString("openTime"),
        closeTime = row.optNullableString("closeTime"),
        subcategories = row.optJSONArray("subcategories")?.let { array ->
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }.orEmpty(),
        description = row.optNullableString("description")
    )
}.toList()

private fun CustomerProduct.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("shopId", shopId)
    .put("name", name)
    .putNullable("description", description)
    .put("price", price)
    .put("deliveryPrice", deliveryPrice)
    .putNullable("image", image)
    .put("available", available)

private fun JSONArray?.toCustomerProducts(expectedShopId: String): List<CustomerProduct> =
    objects().mapNotNull { row ->
        val id = row.optString("id")
        val shopId = row.optString("shopId")
        if (id.isBlank() || shopId != expectedShopId) return@mapNotNull null
        CustomerProduct(
            id = id,
            shopId = shopId,
            name = row.optString("name").ifBlank { "สินค้า" },
            description = row.optNullableString("description"),
            price = row.optDouble("price", 0.0),
            deliveryPrice = row.optDouble("deliveryPrice", row.optDouble("price", 0.0)),
            image = row.optNullableString("image"),
            available = row.optBoolean("available", true)
        )
    }.toList()

private fun HomeBanner.toJson(): JSONObject = JSONObject()
    .put("image", image)
    .putNullable("title", title)
    .putNullable("subtitle", subtitle)
    .putNullable("alt", alt)
    .putNullable("link", link)
    .put("rotationMs", rotationMs)

private fun JSONArray?.toHomeBanners(): List<HomeBanner> = objects().mapNotNull { row ->
    val image = row.optString("image")
    if (image.isBlank()) return@mapNotNull null
    HomeBanner(
        image = image,
        title = row.optNullableString("title"),
        subtitle = row.optNullableString("subtitle"),
        alt = row.optNullableString("alt"),
        link = row.optNullableString("link"),
        rotationMs = row.optLong("rotationMs", 5_000L).coerceAtLeast(1_000L)
    )
}.toList()

private fun ServiceBanner.toJson(): JSONObject = JSONObject()
    .put("key", key)
    .putNullable("image", image)
    .putNullable("title", title)
    .putNullable("subtitle", subtitle)
    .put("active", active)
    .putNullable("link", link)

private fun JSONArray?.toServiceBanners(): List<ServiceBanner> = objects().mapNotNull { row ->
    val key = row.optString("key")
    if (key.isBlank()) return@mapNotNull null
    ServiceBanner(
        key = key,
        image = row.optNullableString("image"),
        title = row.optNullableString("title"),
        subtitle = row.optNullableString("subtitle"),
        active = row.optBoolean("active", true),
        link = row.optNullableString("link")
    )
}.toList()

private fun MarketInfo.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .putNullable("province", province)
    .putNullable("district", district)
    .putNullable("address", address)
    .putNullable("latitude", latitude)
    .putNullable("longitude", longitude)

private fun JSONArray?.toMarkets(): List<MarketInfo> = objects().mapNotNull { row ->
    val id = row.optString("id")
    if (id.isBlank()) return@mapNotNull null
    MarketInfo(
        id = id,
        name = row.optString("name").ifBlank { "ตลาดสด" },
        province = row.optNullableString("province"),
        district = row.optNullableString("district"),
        address = row.optNullableString("address"),
        latitude = row.optNullableDouble("latitude"),
        longitude = row.optNullableDouble("longitude")
    )
}.toList()

private fun MarketShop.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("marketId", marketId)
    .put("marketName", marketName)
    .put("name", name)
    .put("category", category)
    .putNullable("logo", logo)
    .putNullable("cover", cover)
    .putNullable("description", description)
    .put("deliveryEnabled", deliveryEnabled)
    .putNullable("latitude", latitude)
    .putNullable("longitude", longitude)

private fun JSONArray?.toMarketShops(): List<MarketShop> = objects().mapNotNull { row ->
    val id = row.optString("id")
    if (id.isBlank()) return@mapNotNull null
    MarketShop(
        id = id,
        marketId = row.optString("marketId"),
        marketName = row.optString("marketName"),
        name = row.optString("name").ifBlank { "ร้านค้า" },
        category = row.optString("category"),
        logo = row.optNullableString("logo"),
        cover = row.optNullableString("cover"),
        description = row.optNullableString("description"),
        deliveryEnabled = row.optBoolean("deliveryEnabled", false),
        latitude = row.optNullableDouble("latitude"),
        longitude = row.optNullableDouble("longitude")
    )
}.toList()

private fun MarketProduct.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("shopId", shopId)
    .put("marketId", marketId)
    .put("marketName", marketName)
    .put("shopName", shopName)
    .put("name", name)
    .put("category", category)
    .putNullable("description", description)
    .putNullable("image", image)
    .put("price", price)
    .putNullable("unit", unit)
    .put("availablePacks", availablePacks)

private fun JSONArray?.toMarketProducts(): List<MarketProduct> = objects().mapNotNull { row ->
    val id = row.optString("id")
    if (id.isBlank()) return@mapNotNull null
    MarketProduct(
        id = id,
        shopId = row.optString("shopId"),
        marketId = row.optString("marketId"),
        marketName = row.optString("marketName"),
        shopName = row.optString("shopName").ifBlank { "ร้านค้า" },
        name = row.optString("name").ifBlank { "สินค้า" },
        category = row.optString("category"),
        description = row.optNullableString("description"),
        image = row.optNullableString("image"),
        price = row.optDouble("price", 0.0),
        unit = row.optNullableString("unit"),
        availablePacks = row.optInt("availablePacks", 0).coerceAtLeast(0)
    )
}.toList()


private fun CustomerPromotion.toJson(): JSONObject = JSONObject()
    .put("shopId", shopId)
    .put("shopName", shopName)
    .put("title", title)
    .put("description", description)

private fun JSONArray?.toCustomerPromotions(): List<CustomerPromotion> =
    objects().mapNotNull { row ->
        val shopId = row.optString("shopId")
        if (shopId.isBlank()) return@mapNotNull null
        CustomerPromotion(
            shopId = shopId,
            shopName = row.optString("shopName").ifBlank { "ร้านค้า" },
            title = row.optString("title").ifBlank { "โปรโมชั่น" },
            description = row.optString("description")
        )
    }.toList()
