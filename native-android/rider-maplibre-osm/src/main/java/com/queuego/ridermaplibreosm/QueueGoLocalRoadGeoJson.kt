package com.queuego.ridermaplibreosm

internal fun queueGoLocalRoadGeoJson(
    segments: List<QueueGoLocalRoadSegment>
): String {
    val accepted = segments
        .onEach { it.validateForImport() }
        .filter { it.renderable }
        .sortedBy { it.id }

    return buildString {
        append("""{"type":"FeatureCollection","features":[""")
        accepted.forEachIndexed { index, segment ->
            if (index > 0) append(',')
            append("""{"type":"Feature","id":""")
            appendJsonString(segment.id)
            append(""","properties":{""")
            append(""""road_class":""")
            appendJsonString(segment.roadClass.wireName)
            append(""","source":""")
            appendJsonString(segment.source.wireName)
            append(""","verification":""")
            appendJsonString(segment.verification.wireName)
            append(""","access":""")
            appendJsonString(segment.access.wireName)
            append(""","one_way":""").append(segment.oneWay)
            append(""","private_access":""").append(segment.privateAccess)
            segment.widthMeters?.let {
                append(""","width_m":""").append(it)
            }
            append(""","source_ref":""")
            appendJsonString(segment.sourceReference)
            segment.verifiedAtEpochMs?.let {
                append(""","verified_at_ms":""").append(it)
            }
            append("""},"geometry":{"type":"LineString","coordinates":[""")
            segment.points.forEachIndexed { pointIndex, point ->
                if (pointIndex > 0) append(',')
                append('[')
                append(point.longitude)
                append(',')
                append(point.latitude)
                append(']')
            }
            append("]}}")
        }
        append("]}")
    }
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) {
                append("\\u")
                append(ch.code.toString(16).padStart(4, '0'))
            } else {
                append(ch)
            }
        }
    }
    append('"')
}
