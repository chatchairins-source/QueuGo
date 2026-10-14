package com.queuego.ridermaplibreosm

object QueueGoLocalRoadPolicy {
    fun visibleSegments(
        segments: List<QueueGoLocalRoadSegment>
    ): List<QueueGoLocalRoadSegment> =
        segments.filter { segment ->
            segment.validateForImport()
            segment.renderable
        }

    fun motorcycleRoutingSegments(
        segments: List<QueueGoLocalRoadSegment>
    ): List<QueueGoLocalRoadSegment> =
        visibleSegments(segments).filter { it.eligibleForMotorcycleRouting }
}
