package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationMapPoint
import com.queuego.ridernavigationmap.NavigationRouteSegment
import com.queuego.ridernavigationmap.NavigationRouteStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderNavigationSessionRegistryTest {
    @After
    fun tearDown() {
        RiderNavigationSessionRegistry.clearForTests()
    }

    private fun route(id: String, style: NavigationRouteStyle) = NavigationRouteSegment(
        id = id,
        toStopId = "stop-1",
        points = listOf(
            NavigationMapPoint(13.0, 100.0),
            NavigationMapPoint(13.001, 100.001)
        ),
        style = style
    )

    @Test
    fun sameTopologyRestoresFutureAndRenderedRoutes() {
        val future = route("future", NavigationRouteStyle.FUTURE)
        val active = route("active", NavigationRouteStyle.ACTIVE)

        RiderNavigationSessionRegistry.saveFutureRoutes("rider", "topology-a", listOf(future))
        RiderNavigationSessionRegistry.saveRenderedRoutes("rider", "topology-a", listOf(active, future))

        val restored = RiderNavigationSessionRegistry.load("rider", "topology-a")!!
        assertEquals(listOf(future), restored.futureRoutes)
        assertEquals(listOf(active, future), restored.renderedRoutes)
    }

    @Test
    fun emptyTransientFrameDoesNotEraseLastVisibleRoute() {
        val active = route("active", NavigationRouteStyle.ACTIVE)
        RiderNavigationSessionRegistry.saveRenderedRoutes("rider", "topology-a", listOf(active))
        RiderNavigationSessionRegistry.saveRenderedRoutes("rider", "topology-a", emptyList())

        assertEquals(
            listOf(active),
            RiderNavigationSessionRegistry.load("rider", "topology-a")!!.renderedRoutes
        )
    }

    @Test
    fun topologyChangeCannotLeakOldRouteIntoNewJob() {
        RiderNavigationSessionRegistry.saveRenderedRoutes(
            "rider",
            "topology-a",
            listOf(route("old", NavigationRouteStyle.ACTIVE))
        )

        assertNull(RiderNavigationSessionRegistry.load("rider", "topology-b"))
    }
}
