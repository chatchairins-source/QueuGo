package com.queuego.ridermaplibreosm

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class QueueGoCoverageRoadGeometry(
    val id: String,
    val points: List<QueueGoLocalRoadPoint>
) {
    init {
        require(id.isNotBlank()) { "Coverage road id is required" }
        require(points.size >= 2) { "Coverage road requires at least two points" }
    }
}

data class QueueGoAggregateTraceEvidence(
    val aggregateReference: String,
    val points: List<QueueGoLocalRoadPoint>,
    val traversalCount: Int,
    val medianAccuracyMeters: Double,
    val generatedAtEpochMillis: Long
) {
    init {
        require(aggregateReference.startsWith("queuego-trace:")) {
            "Coverage evidence must use a QueueGo aggregate trace reference"
        }
        require(aggregateReference.length <= 240) {
            "Coverage evidence reference is too long"
        }
        require(points.size in 2..10_000) {
            "Coverage evidence point count is invalid"
        }
        require(traversalCount in 1..1_000_000) {
            "Coverage traversal count is invalid"
        }
        require(medianAccuracyMeters.isFinite() && medianAccuracyMeters > 0.0) {
            "Coverage median accuracy is invalid"
        }
        require(generatedAtEpochMillis > 0L) {
            "Coverage generated timestamp is invalid"
        }
    }
}

data class QueueGoLocalRoadCoverageAuditPolicy(
    val minimumTraversalCount: Int = 3,
    val maximumMedianAccuracyMeters: Double = 20.0,
    val minimumTraceLengthMeters: Double = 30.0,
    val baseRoadCoverageDistanceMeters: Double = 18.0,
    val minimumUncoveredFraction: Double = 0.65,
    val endpointJunctionHintDistanceMeters: Double = 25.0
) {
    init {
        require(minimumTraversalCount in 2..100) {
            "Coverage minimum traversal count is invalid"
        }
        require(maximumMedianAccuracyMeters in 3.0..50.0) {
            "Coverage accuracy threshold is invalid"
        }
        require(minimumTraceLengthMeters in 10.0..2_000.0) {
            "Coverage minimum trace length is invalid"
        }
        require(baseRoadCoverageDistanceMeters in 5.0..60.0) {
            "Coverage road-distance threshold is invalid"
        }
        require(minimumUncoveredFraction in 0.5..1.0) {
            "Coverage uncovered fraction is invalid"
        }
        require(endpointJunctionHintDistanceMeters in 5.0..50.0) {
            "Coverage endpoint hint threshold is invalid"
        }
    }
}

enum class QueueGoCoverageEndpointHint {
    NONE,
    NEAR_EXISTING_ROAD
}

data class QueueGoLocalRoadCoverageCandidate(
    val candidateKey: String,
    val sourceReference: String,
    val points: List<QueueGoLocalRoadPoint>,
    val traversalCount: Int,
    val medianAccuracyMeters: Double,
    val lengthMeters: Double,
    val uncoveredFraction: Double,
    val startHint: QueueGoCoverageEndpointHint,
    val endHint: QueueGoCoverageEndpointHint
)

/**
 * Offline/review-only coverage audit.
 *
 * It never promotes evidence into QueueGoLocalRoad, never creates graph junctions and never
 * modifies Production routing. Inputs must already be privacy-safe aggregate trace evidence;
 * Rider/account/order identifiers are intentionally absent from this model.
 */
fun auditQueueGoLocalRoadCoverage(
    baseRoads: List<QueueGoCoverageRoadGeometry>,
    aggregateTraces: List<QueueGoAggregateTraceEvidence>,
    policy: QueueGoLocalRoadCoverageAuditPolicy = QueueGoLocalRoadCoverageAuditPolicy()
): List<QueueGoLocalRoadCoverageCandidate> {
    val seenEvidence = hashSetOf<String>()
    return aggregateTraces
        .asSequence()
        .onEach { evidence ->
            require(seenEvidence.add(evidence.aggregateReference)) {
                "Duplicate coverage evidence reference: " + evidence.aggregateReference
            }
        }
        .filter { it.traversalCount >= policy.minimumTraversalCount }
        .filter { it.medianAccuracyMeters <= policy.maximumMedianAccuracyMeters }
        .mapNotNull { evidence ->
            val lengthMeters = queueGoPolylineLengthMeters(evidence.points)
            if (lengthMeters < policy.minimumTraceLengthMeters) return@mapNotNull null

            val pointDistances = evidence.points.map { point ->
                queueGoDistanceToNearestCoverageRoadMeters(point, baseRoads)
            }
            val uncoveredCount = pointDistances.count {
                it > policy.baseRoadCoverageDistanceMeters
            }
            val uncoveredFraction =
                uncoveredCount.toDouble() / pointDistances.size.toDouble()
            if (uncoveredFraction < policy.minimumUncoveredFraction) {
                return@mapNotNull null
            }

            QueueGoLocalRoadCoverageCandidate(
                candidateKey = "coverage:" + queueGoCoverageStableKey(evidence.aggregateReference),
                sourceReference = evidence.aggregateReference,
                points = evidence.points.toList(),
                traversalCount = evidence.traversalCount,
                medianAccuracyMeters = evidence.medianAccuracyMeters,
                lengthMeters = lengthMeters,
                uncoveredFraction = uncoveredFraction,
                startHint = queueGoCoverageEndpointHint(
                    pointDistances.first(),
                    policy.endpointJunctionHintDistanceMeters
                ),
                endHint = queueGoCoverageEndpointHint(
                    pointDistances.last(),
                    policy.endpointJunctionHintDistanceMeters
                )
            )
        }
        .sortedBy { it.candidateKey }
        .toList()
}

private fun queueGoCoverageEndpointHint(
    distanceMeters: Double,
    thresholdMeters: Double
): QueueGoCoverageEndpointHint =
    if (distanceMeters <= thresholdMeters) {
        QueueGoCoverageEndpointHint.NEAR_EXISTING_ROAD
    } else {
        QueueGoCoverageEndpointHint.NONE
    }

internal fun queueGoPolylineLengthMeters(
    points: List<QueueGoLocalRoadPoint>
): Double =
    points.zipWithNext().sumOf { (a, b) ->
        queueGoLocalRoadDistanceMeters(a, b)
    }

internal fun queueGoDistanceToNearestCoverageRoadMeters(
    point: QueueGoLocalRoadPoint,
    roads: List<QueueGoCoverageRoadGeometry>
): Double {
    if (roads.isEmpty()) return Double.POSITIVE_INFINITY

    var nearest = Double.POSITIVE_INFINITY
    roads.forEach { road ->
        road.points.zipWithNext().forEach { (a, b) ->
            nearest = min(
                nearest,
                queueGoPointToSegmentDistanceMeters(point, a, b)
            )
        }
    }
    return nearest
}

/**
 * Local tangent-plane point-to-segment distance. This is only a coverage-review heuristic over
 * village-scale segments; it does not alter map geometry or routing topology.
 */
internal fun queueGoPointToSegmentDistanceMeters(
    point: QueueGoLocalRoadPoint,
    a: QueueGoLocalRoadPoint,
    b: QueueGoLocalRoadPoint
): Double {
    val referenceLatitude = Math.toRadians((a.latitude + b.latitude + point.latitude) / 3.0)
    val metersPerDegreeLatitude = 111_132.0
    val metersPerDegreeLongitude = 111_320.0 * cos(referenceLatitude)

    fun xy(p: QueueGoLocalRoadPoint): Pair<Double, Double> =
        Pair(
            p.longitude * metersPerDegreeLongitude,
            p.latitude * metersPerDegreeLatitude
        )

    val (px, py) = xy(point)
    val (ax, ay) = xy(a)
    val (bx, by) = xy(b)
    val abx = bx - ax
    val aby = by - ay
    val lengthSquared = abx * abx + aby * aby
    if (lengthSquared <= 0.000_001) {
        val dx = px - ax
        val dy = py - ay
        return sqrt(dx * dx + dy * dy)
    }

    val projection = ((px - ax) * abx + (py - ay) * aby) / lengthSquared
    val t = max(0.0, min(1.0, projection))
    val nearestX = ax + t * abx
    val nearestY = ay + t * aby
    val dx = px - nearestX
    val dy = py - nearestY
    return sqrt(dx * dx + dy * dy)
}

private fun queueGoCoverageStableKey(reference: String): String =
    reference
        .removePrefix("queuego-trace:")
        .lowercase()
        .map { ch -> if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '-' }
        .joinToString("")
        .trim('-')
        .take(100)
        .ifBlank { "aggregate" }
