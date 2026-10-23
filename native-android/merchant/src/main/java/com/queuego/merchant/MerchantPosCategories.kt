package com.queuego.merchant

import java.text.Collator
import java.util.Locale

data class PosProductCategory(val key: String, val label: String)

fun posProductCategoryLabel(category: String?): String = category?.trim().takeUnless { it.isNullOrEmpty() }
    ?: "ไม่จัดหมวด"

fun posProductCategoryKey(category: String?): String =
    posProductCategoryLabel(category).lowercase(Locale("th", "TH"))

fun posProductCategories(products: List<PosProduct>): List<PosProductCategory> {
    val collator = Collator.getInstance(Locale("th", "TH"))
    return products.map { PosProductCategory(posProductCategoryKey(it.category), posProductCategoryLabel(it.category)) }
        .distinctBy { it.key }
        .sortedWith { first, second -> collator.compare(first.label, second.label) }
}

fun filterPosProducts(
    products: List<PosProduct>,
    query: String,
    categoryKey: String
): List<PosProduct> {
    val normalizedQuery = query.trim().lowercase(Locale("th", "TH"))
    return products.filter { product ->
        val categoryLabel = posProductCategoryLabel(product.category)
        val matchesQuery = normalizedQuery.isEmpty() ||
            "${product.name} $categoryLabel".lowercase(Locale("th", "TH")).contains(normalizedQuery)
        val matchesCategory = categoryKey == "all" || posProductCategoryKey(product.category) == categoryKey
        matchesQuery && matchesCategory
    }
}
