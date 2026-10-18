package com.queuego.merchant

import org.json.JSONArray

/** Merchant-only reference imagery is never promoted into a customer-approved image. */
internal fun merchantGrocerySourcePage(rows: JSONArray): MerchantGroceryCatalogPage {
    val items = buildList {
        for (i in 0 until minOf(rows.length(), 50)) {
            val r = rows.optJSONObject(i) ?: continue
            val id = r.optString("source_product_id").trim()
            val title = r.optString("title_th").trim()
            if (id.isBlank() || title.isBlank()) continue
            // Source image_url is provenance/review input only. It is not a reuse grant.
            val image: String? = null
            add(MerchantGroceryCatalogItem(
                id = "source:$id", brand = r.optString("brand"), name = title,
                variant = r.optString("barcode").takeUnless { it == "null" }.orEmpty(),
                sizeValue = 0.0, sizeUnit = "", packSize = 1,
                category = r.optString("queuego_category"), image = image,
                productUpdatedAt = null, enabled = r.optBoolean("in_shop", false),
                sellingPrice = null, stockQty = 0, stockTracking = false,
                posAvailable = true, deliveryAvailable = true, deliveryPrice = null,
                sourceProductId = id
            ))
        }
    }
    return MerchantGroceryCatalogPage(items, rows.length() > 50)
}

internal fun merchantPricedProducts(products: List<MerchantProduct>, grocery: Boolean): List<MerchantProduct> =
    products.filter { !grocery || (it.posPrice ?: it.price).let { price -> price.isFinite() && price > 0.0 } }
