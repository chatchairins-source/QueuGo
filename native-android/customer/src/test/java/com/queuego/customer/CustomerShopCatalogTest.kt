package com.queuego.customer

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CustomerShopCatalogTest {
    private fun product(shop: String) = CustomerProduct("product-$shop", shop, shop, null, 10.0, 11.0, null, true)

    @Test fun slowPreviousShopCannotReplaceNewShopEvenIfHttpIgnoresCancellation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val catalog = CustomerShopCatalog(scope)
        val late = CompletableDeferred<List<CustomerProduct>>()
        catalog.open("first") { withContext(NonCancellable) { late.await() } }
        assertTrue(catalog.state.value.loading)
        catalog.open("second") { listOf(product("second")) }
        late.complete(listOf(product("first")))
        yield()
        assertEquals("second", catalog.state.value.shopId)
        assertEquals(listOf(product("second")), catalog.state.value.products)
        assertFalse(catalog.state.value.loading)
        scope.cancel()
    }

    @Test fun leavingOrLoggingOutDiscardsLateProductsAndErrors() = runBlocking {
        for (failure in listOf(false, true)) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val catalog = CustomerShopCatalog(scope)
            val late = CompletableDeferred<Unit>()
            catalog.open("owned") {
                withContext(NonCancellable) {
                    late.await()
                    if (failure) error("expired request")
                    listOf(product("owned"))
                }
            }
            catalog.close()
            late.complete(Unit)
            yield()
            assertEquals(CustomerShopCatalogState(), catalog.state.value)
            scope.cancel()
        }
    }

    @Test fun failedLoadIsAnErrorAndRetryRecoversWithoutReportingAnEmptyStore() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val catalog = CustomerShopCatalog(scope)
        catalog.open("shop") { error("offline") }
        assertNotNull(catalog.state.value.error)
        assertFalse(catalog.state.value.loading)
        catalog.open("shop") { listOf(product("shop")) }
        assertNull(catalog.state.value.error)
        assertEquals(1, catalog.state.value.products.size)
        scope.cancel()
    }

    @Test fun previousRetryCannotOverwriteMoreRecentRetryInSameShop() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val catalog = CustomerShopCatalog(scope)
        val late = CompletableDeferred<Unit>()
        catalog.open("shop") { withContext(NonCancellable) { late.await(); error("stale failure") } }
        catalog.open("shop") { emptyList() }
        late.complete(Unit)
        yield()
        assertNull(catalog.state.value.error)
        assertFalse(catalog.state.value.loading)
        assertTrue(catalog.state.value.products.isEmpty())
        scope.cancel()
    }
    @Test fun wrongShopPayloadFailsClosedWithoutLeakingProducts() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val catalog = CustomerShopCatalog(scope)
        catalog.open("second") { listOf(product("first")) }
        assertEquals("second", catalog.state.value.shopId)
        assertNotNull(catalog.state.value.error)
        assertTrue(catalog.state.value.products.isEmpty())
        scope.cancel()
    }

    @Test fun screenOwnerRejectsPreviousCatalogBeforeEffectsRun() {
        val state = CustomerShopCatalogState("first", listOf(product("first")))
        assertEquals(state, state.forShop("first"))
        assertEquals(CustomerShopCatalogState("second", loading = true), state.forShop("second"))
        assertEquals(CustomerShopCatalogState(), state.forShop(null))
    }

    @Test fun thirtyRapidShopVisitsDiscardLateSuccessAndFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val catalog = CustomerShopCatalog(scope)
        repeat(30) { round ->
            val late = CompletableDeferred<Unit>()
            catalog.open("first") {
                withContext(NonCancellable) {
                    late.await()
                    if (round % 2 == 0) error("late old error")
                    listOf(product("first"))
                }
            }
            catalog.close()
            catalog.open("second") { listOf(product("second")) }
            late.complete(Unit)
            yield()
            assertEquals("second", catalog.state.value.shopId)
            assertEquals(listOf(product("second")), catalog.state.value.products)
            assertNull(catalog.state.value.error)
            assertFalse(catalog.state.value.loading)
            catalog.close()
            assertEquals(CustomerShopCatalogState(), catalog.state.value)
        }
        scope.cancel()
    }

    private fun groceryRows(shop: String, start: Int, count: Int) = (start until start + count).map {
        CustomerProduct("item-$it", shop, "สินค้า $it", null, 10.0, 11.0, null, true)
    }

    @Test fun grocerySentinelPagesRetryTheSameOffsetWithoutDroppingOrDuplicatingRows() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val catalog = CustomerShopCatalog(scope)
        val offsets = mutableListOf<Int>()
        var failNextPage = true
        var cachedProducts = emptyList<CustomerProduct>()
        catalog.openGroceryCached("shop", { emptyList() }, { listOf("เครื่องดื่ม") }, { _, offset ->
            offsets += offset
            if (offset == 50 && failNextPage) {
                failNextPage = false
                error("temporary outage")
            }
            groceryRows("shop", offset, if (offset < 100) 51 else 1)
        }, { cachedProducts = it })
        assertEquals(50, catalog.state.value.products.size)
        assertTrue(catalog.state.value.grocery!!.hasMore)
        catalog.loadGroceryMore()
        assertEquals(50, catalog.state.value.products.size)
        assertNotNull(catalog.state.value.grocery!!.error)
        assertEquals(50, catalog.state.value.grocery!!.nextOffset)
        catalog.loadGroceryMore()
        assertEquals(100, catalog.state.value.products.size)
        catalog.loadGroceryMore()
        assertEquals(listOf(0, 50, 50, 100), offsets)
        assertEquals(101, catalog.state.value.products.size)
        assertEquals(catalog.state.value.products, cachedProducts)
        assertEquals(101, catalog.state.value.products.map { it.id }.distinct().size)
        assertFalse(catalog.state.value.grocery!!.hasMore)
        assertNull(catalog.state.value.grocery!!.error)
        scope.cancel()
    }

    @Test fun lateGroceryPageCannotReplaceSelectedCategoryAndClosedCatalogIgnoresLoadMore() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val catalog = CustomerShopCatalog(scope)
        val late = CompletableDeferred<List<CustomerProduct>>()
        catalog.openGroceryCached("shop", { emptyList() }, { listOf("เครื่องดื่ม") }, { category, offset ->
            when {
                category.isNotEmpty() -> listOf(product("shop").copy(id = "drink"))
                offset == 0 -> groceryRows("shop", 0, 51)
                else -> withContext(NonCancellable) { late.await() }
            }
        }, {})
        catalog.loadGroceryMore()
        catalog.selectGroceryCategory("เครื่องดื่ม")
        late.complete(groceryRows("shop", 50, 51))
        yield()
        assertEquals("เครื่องดื่ม", catalog.state.value.grocery!!.category)
        assertEquals(listOf("drink"), catalog.state.value.products.map { it.id })
        catalog.close()
        catalog.loadGroceryMore()
        assertEquals(CustomerShopCatalogState(), catalog.state.value)
        scope.cancel()
    }

    @Test fun groceryCategoriesFailureDoesNotHideProductsAndWrongShopPageFailsClosed() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val catalog = CustomerShopCatalog(scope)
        catalog.openGroceryCached("shop", { emptyList() }, { error("category outage") },
            { _, _ -> listOf(product("shop")) }, {})
        assertEquals(1, catalog.state.value.products.size)
        assertTrue(catalog.state.value.grocery!!.categoriesError)
        catalog.openGroceryCached("shop", { emptyList() }, { emptyList() },
            { _, _ -> listOf(product("other")) }, {})
        assertTrue(catalog.state.value.products.isEmpty())
        assertNotNull(catalog.state.value.error)
        scope.cancel()
    }


    @Test fun offlineGroceryCacheRemainsScopedAndExcludesUnsellableRows() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val catalog = CustomerShopCatalog(scope)
        val sellable = product("shop")
        catalog.openGroceryCached("shop", {
            listOf(sellable, product("other"), sellable.copy(id = "free", price = 0.0),
                sellable.copy(id = "unavailable", available = false))
        }, { error("offline") }, { _, _ -> error("offline") }, {})
        assertEquals(listOf(sellable), catalog.state.value.products)
        assertNull(catalog.state.value.error)
        assertNotNull(catalog.state.value.grocery!!.error)
        scope.cancel()
    }

}
