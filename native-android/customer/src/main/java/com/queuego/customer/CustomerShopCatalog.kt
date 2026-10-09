package com.queuego.customer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal data class CustomerShopCatalogState(
    val shopId: String? = null,
    val products: List<CustomerProduct> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/** Each request belongs to one shop visit. Even a non-cooperative late HTTP response
 * cannot replace a newer visit or repopulate a catalog after leaving the page. */
internal class CustomerShopCatalog(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(CustomerShopCatalogState())
    val state: StateFlow<CustomerShopCatalogState> = mutableState
    private var generation = 0L
    private var request: Job? = null

    fun open(shopId: String, fetch: suspend () -> List<CustomerProduct>) {
        val ticket = ++generation
        request?.cancel()
        mutableState.value = CustomerShopCatalogState(shopId = shopId, loading = true)
        request = scope.launch {
            try {
                val rows = fetch()
                if (ticket == generation) mutableState.value = CustomerShopCatalogState(shopId, rows)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (ticket == generation) mutableState.value = CustomerShopCatalogState(shopId = shopId, error = "โหลดสินค้าไม่สำเร็จ")
            }
        }
    }

    fun close() {
        ++generation
        request?.cancel()
        request = null
        mutableState.value = CustomerShopCatalogState()
    }
}
