package com.queuego.ridernavigationmap

enum class NavigationViewMode {
    FOLLOW,
    OVERVIEW,
    FREE_PAN
}

data class NavigationViewModeState(
    val mode: NavigationViewMode = NavigationViewMode.FOLLOW,
    val recenterVisible: Boolean = false
)

class NavigationViewModeController(
    initialMode: NavigationViewMode = NavigationViewMode.FOLLOW
) {
    private var state = stateFor(initialMode)

    fun state(): NavigationViewModeState = state

    fun onUserGesture(): NavigationViewModeState {
        state = stateFor(NavigationViewMode.FREE_PAN)
        return state
    }

    fun onRecenter(): NavigationViewModeState {
        state = stateFor(NavigationViewMode.FOLLOW)
        return state
    }

    fun onOverview(): NavigationViewModeState {
        state = stateFor(NavigationViewMode.OVERVIEW)
        return state
    }

    fun onActiveStopChanged(): NavigationViewModeState {
        if (state.mode == NavigationViewMode.OVERVIEW) {
            state = stateFor(NavigationViewMode.FOLLOW)
        }
        return state
    }

    fun restore(mode: NavigationViewMode): NavigationViewModeState {
        state = stateFor(mode)
        return state
    }

    private fun stateFor(mode: NavigationViewMode) = NavigationViewModeState(
        mode = mode,
        recenterVisible = mode != NavigationViewMode.FOLLOW
    )
}
