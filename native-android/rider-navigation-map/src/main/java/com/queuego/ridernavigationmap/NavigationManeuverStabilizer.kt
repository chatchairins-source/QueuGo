package com.queuego.ridernavigationmap

data class NavigationDisplayManeuver(
    val routeId: String,
    val instruction: NavigationRouteInstruction,
    val distanceToInstructionMeters: Double?
)

class NavigationManeuverStabilizer(
    private val requiredForwardConfirmations: Int = 2,
    private val immediateArrivalMeters: Double = 30.0
) {
    private var current: NavigationDisplayManeuver? = null
    private var pendingSequence: Int? = null
    private var pendingCount: Int = 0

    fun reset() {
        current = null
        pendingSequence = null
        pendingCount = 0
    }

    fun current(): NavigationDisplayManeuver? = current

    fun update(
        routeId: String,
        instruction: NavigationRouteInstruction?,
        distanceToInstructionMeters: Double?,
        weakGps: Boolean = false,
        offRoute: Boolean = false
    ): NavigationDisplayManeuver? {
        if (routeId.isBlank()) {
            reset()
            return null
        }

        val existing = current
        if (existing != null && existing.routeId != routeId) {
            reset()
        }

        val candidate = instruction ?: return current
        val normalizedDistance = distanceToInstructionMeters
            ?.takeIf { it.isFinite() && it >= 0.0 }

        val active = current
        if (active == null) {
            return accept(routeId, candidate, normalizedDistance)
        }

        if (weakGps || offRoute) {
            return active
        }

        if (candidate.sequence < active.instruction.sequence) {
            clearPending()
            return active
        }

        if (candidate.sequence == active.instruction.sequence) {
            clearPending()
            current = active.copy(
                instruction = candidate,
                distanceToInstructionMeters = normalizedDistance
            )
            return current
        }

        val isImmediateArrival =
            candidate.turnCode == 9 &&
                normalizedDistance != null &&
                normalizedDistance <= immediateArrivalMeters

        if (isImmediateArrival) {
            return accept(routeId, candidate, normalizedDistance)
        }

        if (pendingSequence == candidate.sequence) {
            pendingCount += 1
        } else {
            pendingSequence = candidate.sequence
            pendingCount = 1
        }

        return if (pendingCount >= requiredForwardConfirmations.coerceAtLeast(1)) {
            accept(routeId, candidate, normalizedDistance)
        } else {
            active
        }
    }

    private fun accept(
        routeId: String,
        instruction: NavigationRouteInstruction,
        distanceToInstructionMeters: Double?
    ): NavigationDisplayManeuver {
        val accepted = NavigationDisplayManeuver(
            routeId = routeId,
            instruction = instruction,
            distanceToInstructionMeters = distanceToInstructionMeters
        )
        current = accepted
        clearPending()
        return accepted
    }

    private fun clearPending() {
        pendingSequence = null
        pendingCount = 0
    }
}
