package com.queuego.merchant

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MerchantShopMapRuntimeTest {
    @Test
    fun editShopMapMountsBackgroundsAndRecreatesWithoutCrash() {
        ActivityScenario.launch(MerchantShopMapRuntimeActivity::class.java).use { scenario ->
            fun awaitMap() {
                var ready: CountDownLatch? = null
                scenario.onActivity { ready = it.mapReady }
                assertTrue(
                    "Merchant edit-shop Longdo picker did not become ready",
                    ready!!.await(30, TimeUnit.SECONDS)
                )
            }

            awaitMap()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitMap()
            scenario.recreate()
            awaitMap()
        }
    }
}
