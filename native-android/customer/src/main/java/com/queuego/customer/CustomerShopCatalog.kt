package com.queuego.customer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal data class CustomerGroceryPaging(
    val categories: List<String> = emptyList(),
    val category: String = "",
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val categoriesError: Boolean = false,
    val nextOffset: Int = 0
)

internal data class CustomerShopCatalogState(
    val shopId: String? = null,
    val products: List<CustomerProduct> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val grocery: CustomerGroceryPaging? = null
)

/** Each request belongs to one shop visit. Even a non-cooperative late HTTP response
 * cannot replace a newer visit or repopulate a catalog after leaving the page. */
internal class CustomerShopCatalog(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(CustomerShopCatalogState())
    val state: StateFlow<CustomerShopCatalogState> = mutableState
    private var generation = 0L
    private var request: Job? = null
    private var groceryFetch: (suspend (String, Int) -> List<CustomerProduct>)? = null
    private var groceryPersist: (suspend (List<CustomerProduct>) -> Unit)? = null

    fun open(shopId: String, fetch: suspend () -> List<CustomerProduct>) {
        groceryFetch = null
        groceryPersist = null
        val ticket = ++generation
        request?.cancel()
        mutableState.value = CustomerShopCatalogState(shopId = shopId, loading = true)
        request = scope.launch {
            try {
                val rows = fetch()
                require(rows.all { it.shopId == shopId }) { "Catalog response belongs to another shop" }
                if (ticket == generation) mutableState.value = CustomerShopCatalogState(shopId, rows)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (ticket == generation) mutableState.value = CustomerShopCatalogState(shopId = shopId, error = "โหลดสินค้าไม่สำเร็จ")
            }
        }
    }

    fun openCached(
        shopId: String,
        loadCached: suspend () -> List<CustomerProduct>,
        fetch: suspend () -> List<CustomerProduct>,
        persist: suspend (List<CustomerProduct>) -> Unit
    ) {
        groceryFetch = null
        groceryPersist = null
        val ticket = ++generation
        request?.cancel()
        mutableState.value = CustomerShopCatalogState(shopId = shopId, loading = true)
        request = scope.launch {
            var cached = emptyList<CustomerProduct>()
            try {
                cached = loadCached().filter { it.shopId == shopId }
                if (ticket != generation) return@launch
                if (cached.isNotEmpty()) {
                    mutableState.value = CustomerShopCatalogState(
                        shopId = shopId,
                        products = cached,
                        loading = false
                    )
                }

                val rows = fetch()
                require(rows.all { it.shopId == shopId }) { "Catalog response belongs to another shop" }
                if (ticket == generation) {
                    mutableState.value = CustomerShopCatalogState(shopId, rows)
                    runCatching { persist(rows) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (ticket == generation) {
                    mutableState.value = if (cached.isNotEmpty()) {
                        CustomerShopCatalogState(shopId = shopId, products = cached)
                    } else {
                        CustomerShopCatalogState(shopId = shopId, error = "โหลดสินค้าไม่สำเร็จ")
                    }
                }
            }
        }
    }

    fun openGroceryCached(
        shopId: String,
        loadCached: suspend () -> List<CustomerProduct>,
        loadCategories: suspend () -> List<String>,
        fetchPage: suspend (String, Int) -> List<CustomerProduct>,
        persist: suspend (List<CustomerProduct>) -> Unit
    ) {
        val ticket = ++generation
        request?.cancel()
        groceryFetch = fetchPage
        groceryPersist = persist
        mutableState.value = CustomerShopCatalogState(shopId = shopId, loading = true,
            grocery = CustomerGroceryPaging())
        request = scope.launch {
            var cached = emptyList<CustomerProduct>()
            var categories = emptyList<String>()
            var categoriesError = false
            try {
                cached = runCatching { loadCached() }.getOrDefault(emptyList())
                    .filter { it.shopId == shopId && it.available && it.price > 0 }
                if (ticket != generation) return@launch
                if (cached.isNotEmpty()) mutableState.value = CustomerShopCatalogState(
                    shopId = shopId, products = cached, grocery = CustomerGroceryPaging())
                try {
                    categories = loadCategories().filter { it.isNotBlank() }.distinct()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    categoriesError = true
                }
                if (ticket != generation) return@launch
                val rows = fetchPage("", 0)
                validateGroceryPage(shopId, rows)
                if (ticket != generation) return@launch
                val page = rows.take(50)
                mutableState.value = CustomerShopCatalogState(shopId, page,
                    grocery = CustomerGroceryPaging(categories = categories, hasMore = rows.size > 50,
                        categoriesError = categoriesError, nextOffset = 50))
                runCatching { persist(page) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (ticket == generation) mutableState.value = CustomerShopCatalogState(
                    shopId = shopId, products = cached,
                    error = if (cached.isEmpty()) "โหลดสินค้าไม่สำเร็จ" else null,
                    grocery = CustomerGroceryPaging(categories = categories, categoriesError = categoriesError,
                        error = "โหลดสินค้าไม่สำเร็จ"))
            }
        }
    }

    fun selectGroceryCategory(category: String) {
        val current = mutableState.value
        val paging = current.grocery ?: return
        if (category.isNotEmpty() && category !in paging.categories) return
        if (category == paging.category && paging.error == null) return
        fetchGroceryPage(category, 0, append = false)
    }

    fun loadGroceryMore() {
        val current = mutableState.value
        val paging = current.grocery ?: return
        if (current.loading || paging.loadingMore || (!paging.hasMore && paging.error == null)) return
        fetchGroceryPage(paging.category, paging.nextOffset, append = paging.nextOffset > 0)
    }

    private fun fetchGroceryPage(category: String, offset: Int, append: Boolean) {
        val before = mutableState.value
        val shopId = before.shopId ?: return
        val paging = before.grocery ?: return
        val fetch = groceryFetch ?: return
        val persist = groceryPersist
        val ticket = ++generation
        request?.cancel()
        mutableState.value = before.copy(products = if (append) before.products else emptyList(),
            loading = !append, error = null,
            grocery = paging.copy(category = category, hasMore = false, loadingMore = append,
                error = null, nextOffset = offset))
        request = scope.launch {
            try {
                val rows = fetch(category, offset)
                validateGroceryPage(shopId, rows)
                if (ticket != generation) return@launch
                val page = rows.take(50)
                val products = (if (append) before.products + page else page).distinctBy { it.id }
                mutableState.value = CustomerShopCatalogState(shopId, products,
                    grocery = paging.copy(category = category, hasMore = rows.size > 50,
                        loadingMore = false, error = null, nextOffset = offset + 50))
                if (category.isEmpty() && persist != null) runCatching { persist(products) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (ticket == generation) mutableState.value = before.copy(
                    products = if (append) before.products else emptyList(), loading = false,
                    error = if (append) null else "โหลดสินค้าไม่สำเร็จ",
                    grocery = paging.copy(category = category, loadingMore = false,
                        error = "โหลดสินค้าไม่สำเร็จ", nextOffset = offset))
            }
        }
    }

    private fun validateGroceryPage(shopId: String, rows: List<CustomerProduct>) {
        require(rows.size <= 51 && rows.all { it.shopId == shopId && it.available && it.price > 0 }) {
            "Invalid Grocery catalog page"
        }
    }

    fun close() {
        groceryFetch = null
        groceryPersist = null
        ++generation
        request?.cancel()
        request = null
        mutableState.value = CustomerShopCatalogState()
    }
}

/** Reject a catalog from a previous screen even before navigation effects run. */
internal fun CustomerShopCatalogState.forShop(shopId: String?): CustomerShopCatalogState =
    takeIf { shopId != null && this.shopId == shopId }
        ?: CustomerShopCatalogState(shopId = shopId, loading = shopId != null)
