package com.queuego.customer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CustomerLaundryCatalogTest {
    private fun snapshot() = JSONObject("""
        {
          "enabled": true,
          "hubs": [{
            "id": "eligible-hub", "shop_id": "shop", "name": "ร้านฝากซัก",
            "settings": {"minimum_order": 100, "pickup_fee": 20, "return_fee": 30,
              "round_trip_fee": 45, "delivery_fee_mode": "separate"},
            "services": [{"id": "service", "name": "ซักอบ", "price": 40,
              "pricing_type": "per_kg", "estimated_minutes": 120}]
          }]
        }
    """.trimIndent())

    @Test fun authoritativeSnapshotSuppliesHubSettingsAndEmbeddedServices() {
        val catalog = parseCustomerLaundryCatalog(snapshot())
        assertTrue(catalog.featureEnabled)
        assertEquals(listOf("eligible-hub"), catalog.hubs.map { it.id })
        val settings = catalog.settings.getValue("eligible-hub")
        assertEquals(100.0, settings.minimumOrder, 0.001)
        assertEquals(50.0, settings.deliveryFee, 0.001)
        assertEquals(45.0, settings.copy(deliveryFeeMode = "round_trip").deliveryFee, 0.001)
        val service = catalog.servicesForCustomerHub("eligible-hub").single()
        assertEquals("eligible-hub", service.hubId)
        assertEquals("per_kg", service.pricingType)
        assertEquals(40.0, service.price, 0.001)
    }

    @Test fun disabledOrNoLongerReturnedHubCannotSupplyBookableServices() {
        val disabled = parseCustomerLaundryCatalog(snapshot().put("enabled", false))
        assertFalse(disabled.featureEnabled)
        assertTrue(disabled.hubs.isEmpty())
        assertTrue(disabled.servicesByHub.isEmpty())
        assertThrows(IllegalStateException::class.java) { disabled.servicesForCustomerHub("eligible-hub") }
        val eligible = parseCustomerLaundryCatalog(snapshot())
        assertThrows(IllegalStateException::class.java) { eligible.servicesForCustomerHub("raw-table-only-hub") }
    }

    @Test fun malformedOrEmptyServiceSnapshotDoesNotCreateAnEligibleHub() {
        val raw = snapshot()
        raw.getJSONArray("hubs").getJSONObject(0).put("services", JSONArray())
        val catalog = parseCustomerLaundryCatalog(raw)
        assertTrue(catalog.hubs.isEmpty())
        assertTrue(catalog.settings.isEmpty())
        assertThrows(IllegalStateException::class.java) { catalog.servicesForCustomerHub("eligible-hub") }
        val missingSettings = snapshot()
        missingSettings.getJSONArray("hubs").getJSONObject(0).remove("settings")
        assertTrue(parseCustomerLaundryCatalog(missingSettings).hubs.isEmpty())
    }
}
