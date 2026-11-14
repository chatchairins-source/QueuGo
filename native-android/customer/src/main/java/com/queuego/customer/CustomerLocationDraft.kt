package com.queuego.customer

/** One picker visit owns its draft. Reverse-geocode results require the exact revision,
 * including A -> B -> A, and never overwrite manually entered address details. */
internal data class CustomerLocationDraft(
    val point: CustomerLocation?,
    val address: String,
    val manualAddress: Boolean = false,
    val revision: Long = 0
) {
    fun move(next: CustomerLocation): CustomerLocationDraft {
        if (next.latitude !in 5.0..21.0 || next.longitude !in 97.0..106.0) return this
        if (point?.latitude == next.latitude && point.longitude == next.longitude) return this
        val nextAddress = if (manualAddress) address else ""
        return copy(point = next.copy(address = nextAddress), address = nextAddress, revision = revision + 1)
    }

    fun edit(text: String) = copy(address = text, manualAddress = text.isNotBlank(), revision = revision + 1)

    fun resolved(ticket: Long, text: String?): CustomerLocationDraft {
        if (ticket != revision || manualAddress || text.isNullOrBlank()) return this
        return copy(address = text.trim())
    }
}
