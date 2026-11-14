package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationRouteSegment

internal data class RiderNavigationRetainedFrame(
    val topologyKey: String,
    val futureRoutes: List<NavigationRouteSegment> = emptyList(),
    val renderedRoutes: List<NavigationRouteSegment> = emptyList()
)

internal object RiderNavigationSessionRegistry {
    private val frames = linkedMapOf<String, RiderNavigationRetainedFrame>()

    @Synchronized
    fun load(stateKey: String, topologyKey: String): RiderNavigationRetainedFrame? {
        val frame = frames[stateKey] ?: return null
        if (frame.topologyKey != topologyKey) {
            frames.remove(stateKey)
            return null
        }
        return frame.copy(
            futureRoutes = frame.futureRoutes.toList(),
            renderedRoutes = frame.renderedRoutes.toList()
        )
    }

    @Synchronized
    fun saveFutureRoutes(
        stateKey: String,
        topologyKey: String,
        routes: List<NavigationRouteSegment>
    ) {
        if (stateKey.isBlank()) return
        val current = frames[stateKey]?.takeIf { it.topologyKey == topologyKey }
        frames[stateKey] = RiderNavigationRetainedFrame(
            topologyKey = topologyKey,
            futureRoutes = routes.toList(),
            renderedRoutes = current?.renderedRoutes.orEmpty()
        )
    }

    @Synchronized
    fun saveRenderedRoutes(
        stateKey: String,
        topologyKey: String,
        routes: List<NavigationRouteSegment>
    ) {
        if (stateKey.isBlank()) return
        val current = frames[stateKey]?.takeIf { it.topologyKey == topologyKey }
        val stableRoutes = when {
            routes.isNotEmpty() -> routes.toList()
            current?.renderedRoutes?.isNotEmpty() == true -> current.renderedRoutes
            else -> emptyList()
        }
        frames[stateKey] = RiderNavigationRetainedFrame(
            topologyKey = topologyKey,
            futureRoutes = current?.futureRoutes.orEmpty(),
            renderedRoutes = stableRoutes
        )
    }

    @Synchronized
    fun clear(stateKey: String) {
        frames.remove(stateKey)
    }

    @Synchronized
    internal fun clearForTests() {
        frames.clear()
    }
}
