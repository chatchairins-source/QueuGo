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
}
