package com.queuego.rider

import android.app.ApplicationExitInfo
import com.queuego.ridernavigationmap.NavigationMapDiagnostics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderRuntimeDiagnosticsTest {
    @Test fun retainedMapSessionIsHealthyWithoutRecreationOrBridgeFailure() {
        val health = riderMapRuntimeHealth(
            NavigationMapDiagnostics(
                viewCreatedCount = 1,
                mapCreatedCount = 1,
                routeLayerRebuildCount = 2,
                stopMutationCount = 3,
                renderPassCount = 40,
                snapshotBridgeFailureCount = 0,
                mapReplacementCount = 0
            )
        )
        assertTrue(health.healthy)
        assertTrue(health.detail.contains("map 1"))
    }

    @Test fun recreatedOrFailedMapSessionIsFlagged() {
        val recreated = riderMapRuntimeHealth(
            NavigationMapDiagnostics(
                viewCreatedCount = 2,
                mapCreatedCount = 2,
                routeLayerRebuildCount = 1,
                stopMutationCount = 1,
                snapshotBridgeFailureCount = 0,
                mapReplacementCount = 1
            )
        )
        assertFalse(recreated.healthy)

        val bridgeFailure = riderMapRuntimeHealth(
            NavigationMapDiagnostics(
                viewCreatedCount = 1,
                mapCreatedCount = 1,
                routeLayerRebuildCount = 1,
                stopMutationCount = 1,
                snapshotBridgeFailureCount = 1
            )
        )
        assertFalse(bridgeFailure.healthy)
    }

    @Test fun processExitClassificationKeepsNormalUserExitSeparateFromCrash() {
        assertTrue(riderExitReasonProblematic(ApplicationExitInfo.REASON_CRASH))
        assertTrue(riderExitReasonProblematic(ApplicationExitInfo.REASON_ANR))
        assertFalse(riderExitReasonProblematic(ApplicationExitInfo.REASON_USER_REQUESTED))
        assertFalse(riderExitReasonProblematic(ApplicationExitInfo.REASON_PACKAGE_UPDATED))
    }
}
