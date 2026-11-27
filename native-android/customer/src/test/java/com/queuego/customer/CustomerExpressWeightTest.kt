package com.queuego.customer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerExpressWeightTest {
    @Test fun acceptsWeightsWithinVerifiedVehicleCapacity() {
        assertTrue(validExpressWeightKg(0.1))
        assertTrue(validExpressWeightKg(1000.0))
    }

    @Test fun rejectsImpossibleOrNonFiniteWeights() {
        listOf(null, 0.0, -1.0, 1000.01, Double.POSITIVE_INFINITY, Double.NaN)
            .forEach { assertFalse(validExpressWeightKg(it)) }
    }
}
