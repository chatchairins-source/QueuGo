package com.queuego.ridermaplibreosm

private const val QUEUEGO_LOCAL_ROAD_SCHEMA_VERSION = 1
private const val QUEUEGO_LOCAL_ROAD_MAX_ROADS = 20_000
private const val QUEUEGO_LOCAL_ROAD_MAX_POINTS_PER_ROAD = 10_000
private const val QUEUEGO_LOCAL_ROAD_MAX_TOTAL_POINTS = 250_000
private const val QUEUEGO_LOCAL_ROAD_MAX_SOURCE_REFERENCE_LENGTH = 240

/**
 * Versioned input contract for a reviewed QueueGo Local Roads snapshot.
 *
 * This contract intentionally contains only final road geometry and provenance metadata.
 * Raw Rider GPS trails, account identifiers and competitor/private map geometry do not belong
 * in this feed.
 */
data class QueueGoLocalRoadFeed(
    val schemaVersion: Int = QUEUEGO_LOCAL_ROAD_SCHEMA_VERSION,
    val revision: Long,
    val generatedAtEpochMillis: Long,
    val records: List<QueueGoLocalRoadFeedRecord>
)

data class QueueGoLocalRoadFeedRecord(
    val road: QueueGoLocalRoad,
    val sourceReference: String,
    val verifiedAtEpochMillis: Long? = null
)

enum class QueueGoLocalRoadFeedDecision {
    APPLY,
    UNCHANGED,
    REJECT_STALE
}

data class QueueGoValidatedLocalRoadFeed(
    val revision: Long,
    val generatedAtEpochMillis: Long,
    val roads: List<QueueGoLocalRoad>,
    val decision: QueueGoLocalRoadFeedDecision,
    val totalPointCount: Int
)

fun validateQueueGoLocalRoadFeed(
    feed: QueueGoLocalRoadFeed,
    currentRevision: Long? = null
): QueueGoValidatedLocalRoadFeed {
    require(feed.schemaVersion == QUEUEGO_LOCAL_ROAD_SCHEMA_VERSION) {
        "Unsupported local-road schema version"
    }
    require(feed.revision > 0L) { "Local-road revision must be positive" }
    require(feed.generatedAtEpochMillis > 0L) {
        "Local-road generated timestamp must be positive"
    }
    require(feed.records.size <= QUEUEGO_LOCAL_ROAD_MAX_ROADS) {
        "Local-road feed exceeds road limit"
    }

    val decision = when {
        currentRevision == null -> QueueGoLocalRoadFeedDecision.APPLY
        feed.revision > currentRevision -> QueueGoLocalRoadFeedDecision.APPLY
        feed.revision == currentRevision -> QueueGoLocalRoadFeedDecision.UNCHANGED
        else -> QueueGoLocalRoadFeedDecision.REJECT_STALE
    }

    val seenIds = HashSet<String>(feed.records.size.coerceAtLeast(16))
    var totalPoints = 0

    feed.records.forEach { record ->
        val road = record.road
        require(seenIds.add(road.id)) {
            "Duplicate local-road id: " + road.id
        }
        require(road.points.size <= QUEUEGO_LOCAL_ROAD_MAX_POINTS_PER_ROAD) {
            "Local road exceeds point limit: " + road.id
        }

        totalPoints += road.points.size
        require(totalPoints <= QUEUEGO_LOCAL_ROAD_MAX_TOTAL_POINTS) {
            "Local-road feed exceeds total point limit"
        }

        val sourceReference = record.sourceReference.trim()
        require(sourceReference.isNotEmpty()) {
            "Local-road source reference is required: " + road.id
        }
        require(sourceReference.length <= QUEUEGO_LOCAL_ROAD_MAX_SOURCE_REFERENCE_LENGTH) {
            "Local-road source reference is too long: " + road.id
        }

        if (road.verified) {
            require(record.verifiedAtEpochMillis != null && record.verifiedAtEpochMillis > 0L) {
                "Verified local road requires verification timestamp: " + road.id
            }
            require(record.verifiedAtEpochMillis <= feed.generatedAtEpochMillis) {
                "Verification timestamp cannot be newer than feed: " + road.id
            }
        }

        require(
            road.source in setOf(
                QueueGoLocalRoadSource.OPENSTREETMAP_VERIFIED,
                QueueGoLocalRoadSource.PUBLIC_DATA,
                QueueGoLocalRoadSource.RIDER_GPS_TRACE_VERIFIED,
                QueueGoLocalRoadSource.MERCHANT_CONFIRMED,
                QueueGoLocalRoadSource.FIELD_SURVEY
            )
        ) {
            "Unsupported local-road provenance: " + road.id
        }
    }

    return QueueGoValidatedLocalRoadFeed(
        revision = feed.revision,
        generatedAtEpochMillis = feed.generatedAtEpochMillis,
        roads = feed.records.map { it.road },
        decision = decision,
        totalPointCount = totalPoints
    )
}

/**
 * Only APPLY snapshots are eligible to replace the currently cached road set.
 * UNCHANGED and REJECT_STALE are deliberately side-effect free.
 */
fun QueueGoValidatedLocalRoadFeed.roadsForApply(): List<QueueGoLocalRoad>? =
    takeIf { decision == QueueGoLocalRoadFeedDecision.APPLY }?.roads
