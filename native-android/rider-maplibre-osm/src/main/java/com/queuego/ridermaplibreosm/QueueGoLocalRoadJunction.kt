package com.queuego.ridermaplibreosm

data class QueueGoLocalRoadJunctionMember(
    val roadId: String,
    val pointIndex: Int
)

data class QueueGoVerifiedLocalRoadJunction(
    val id: String,
    val members: List<QueueGoLocalRoadJunctionMember>,
    val source: QueueGoLocalRoadSource,
    val sourceReference: String,
    val verifiedAtEpochMillis: Long
)

internal fun validateQueueGoVerifiedJunction(
    junction: QueueGoVerifiedLocalRoadJunction,
    roadsById: Map<String, QueueGoLocalRoad>,
    generatedAtEpochMillis: Long
) {
    require(junction.id.matches(Regex("^[a-z0-9][a-z0-9:_-]{2,119}$"))) {
        "Local-road junction id is invalid"
    }
    require(junction.members.size in 2..8) {
        "Local-road junction must contain between 2 and 8 members"
    }
    require(junction.members.distinct().size == junction.members.size) {
        "Local-road junction contains duplicate members"
    }
    require(junction.members.map { it.roadId }.distinct().size >= 2) {
        "Local-road junction must connect at least two roads"
    }
    require(junction.sourceReference.length in 1..240) {
        "Local-road junction source reference is invalid"
    }
    require(queueGoLocalRoadSourceReferenceAllowed(junction.source, junction.sourceReference)) {
        "Local-road junction source reference does not match provenance"
    }
    require(junction.verifiedAtEpochMillis > 0L &&
        junction.verifiedAtEpochMillis <= generatedAtEpochMillis
    ) {
        "Local-road junction verification timestamp is invalid"
    }

    val points = junction.members.map { member ->
        require(member.roadId.isNotBlank()) {
            "Local-road junction member road id is required"
        }
        val road = roadsById[member.roadId]
            ?: throw IllegalArgumentException(
                "Local-road junction references unknown road: " + member.roadId
            )
        require(road.verified) {
            "Local-road junction may reference verified roads only: " + member.roadId
        }
        require(road.access != QueueGoLocalRoadAccess.PRIVATE_RESTRICTED) {
            "Local-road junction may not expose a private/restricted road: " + member.roadId
        }
        require(member.pointIndex in road.points.indices) {
            "Local-road junction point index is invalid: " + member.roadId
        }
        road.points[member.pointIndex]
    }

    var maxSpanMeters = 0.0
    points.forEachIndexed { index, a ->
        for (otherIndex in index + 1 until points.size) {
            maxSpanMeters = maxOf(
                maxSpanMeters,
                queueGoLocalRoadDistanceMeters(a, points[otherIndex])
            )
        }
    }
    require(maxSpanMeters <= QUEUEGO_LOCAL_ROAD_MAX_JUNCTION_SPAN_METERS) {
        "Local-road junction members are too far apart"
    }
}

internal const val QUEUEGO_LOCAL_ROAD_MAX_JUNCTION_SPAN_METERS = 25.0
