package com.queuego.ridermaplibreosm

enum class QueueGoLocalRoadSource(val wireName: String) {
    OPENSTREETMAP("openstreetmap"),
    RIDER_TRACE_AGGREGATE("rider_trace_aggregate"),
    MERCHANT_VERIFIED("merchant_verified"),
    FIELD_SURVEY("field_survey"),
    OPEN_GOVERNMENT("open_government")
}

enum class QueueGoLocalRoadVerification(val wireName: String) {
    CANDIDATE("candidate"),
    VERIFIED("verified"),
    RETIRED("retired")
}

enum class QueueGoLocalRoadClass(val wireName: String) {
    RESIDENTIAL("residential"),
    LIVING_STREET("living_street"),
    SERVICE("service"),
    ALLEY("alley"),
    TRACK("track"),
    OTHER("other")
}

enum class QueueGoLocalRoadAccess(val wireName: String) {
    MOTORCYCLE("motorcycle"),
    MOTOR_VEHICLE("motor_vehicle"),
    DELIVERY("delivery"),
    FOOT("foot"),
    UNKNOWN("unknown")
}

data class QueueGoLocalRoadPoint(
    val latitude: Double,
    val longitude: Double
) {
    val valid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class QueueGoLocalRoadSegment(
    val id: String,
    val points: List<QueueGoLocalRoadPoint>,
    val roadClass: QueueGoLocalRoadClass,
    val source: QueueGoLocalRoadSource,
    val verification: QueueGoLocalRoadVerification,
    val access: QueueGoLocalRoadAccess = QueueGoLocalRoadAccess.UNKNOWN,
    val oneWay: Boolean = false,
    val privateAccess: Boolean = false,
    val widthMeters: Double? = null,
    val sourceReference: String,
    val verifiedAtEpochMs: Long? = null
) {
    val renderable: Boolean
        get() = verification != QueueGoLocalRoadVerification.RETIRED &&
            id.isNotBlank() &&
            points.size >= 2 &&
            points.all { it.valid } &&
            sourceReference.isNotBlank()

    val eligibleForMotorcycleRouting: Boolean
        get() = renderable &&
            verification == QueueGoLocalRoadVerification.VERIFIED &&
            !privateAccess &&
            access in setOf(
                QueueGoLocalRoadAccess.MOTORCYCLE,
                QueueGoLocalRoadAccess.DELIVERY,
                QueueGoLocalRoadAccess.MOTOR_VEHICLE
            )
}

internal fun QueueGoLocalRoadSegment.validateForImport() {
    require(id.isNotBlank()) { "Local-road id is required" }
    require(id.length <= 160) { "Local-road id is too long" }
    require(points.size >= 2) { "Local-road geometry requires at least two points" }
    require(points.size <= 8_192) { "Local-road geometry is unreasonably large" }
    require(points.all { it.valid }) { "Local-road geometry contains an invalid coordinate" }
    require(sourceReference.isNotBlank()) { "Local-road provenance reference is required" }
    require(sourceReference.length <= 256) { "Local-road provenance reference is too long" }
    require(widthMeters == null || (widthMeters.isFinite() && widthMeters in 0.5..40.0)) {
        "Local-road width is outside the accepted range"
    }
    if (verification == QueueGoLocalRoadVerification.VERIFIED) {
        require(verifiedAtEpochMs != null && verifiedAtEpochMs > 0L) {
            "Verified local roads require a verification timestamp"
        }
    }
}
