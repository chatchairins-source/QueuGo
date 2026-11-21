package com.queuego.merchant

import org.json.JSONArray

/** Merchant-only reference imagery is never promoted into a customer-approved image. */
private const val MERCHANT_GROCERY_REFERENCE_IMAGE_PREFIX =
    "https://images.mango-prod.siammakro.cloud/"

internal fun merchantGroceryReferenceImage(raw: String?): String? =
    raw?.trim()?.takeIf { it.startsWith(MERCHANT_GROCERY_REFERENCE_IMAGE_PREFIX) }

internal fun merchantGrocerySourcePage(rows: JSONArray): MerchantGroceryCatalogPage {
    val items = buildList {
        for (i in 0 until minOf(rows.length(), 50)) {
            val r = rows.optJSONObject(i) ?: continue
            val id = r.optString("source_product_id").trim()
            val title = r.optString("title_th").trim()
            if (id.isBlank() || title.isBlank()) continue
            // The preview remains Merchant-only. qg_grocery_source_add_to_shop keeps
            // products.image null and stores this URL only as catalog_preview_image metadata.
            val image = merchantGroceryReferenceImage(r.optString("image_url"))
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
