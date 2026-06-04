package com.gregor.n2kandroid.nmea

data class DiscoveredDevice(
    val address: Int,
    val name: ULong? = null,
    val uniqueNumber: Int? = null,
    val manufacturerCode: Int? = null,
    val deviceFunction: Int? = null,
    val deviceClass: Int? = null,
    val industryGroup: Int? = null,
    val productCode: Int? = null,
    val modelId: String? = null,
    val softwareVersion: String? = null,
    val modelVersion: String? = null,
    val serialCode: String? = null,
    val nmeaVersion: String? = null,
    val loadEquivalency: Int? = null,
    val lastSeenMillis: Long = 0L,
    val frameCount: Int = 0,
    val pgnStats: List<PgnTrafficStat> = emptyList(),
) {
    val displayName: String
        get() = modelId?.takeIf { it.isNotBlank() }
            ?: serialCode?.takeIf { it.isNotBlank() }
            ?: "Device $address"

    val details: String
        get() = listOfNotNull(
            manufacturerCode?.let { "Mfr $it" },
            deviceClass?.let { "Class $it" },
            deviceFunction?.let { "Function $it" },
            productCode?.let { "Product $it" },
        ).joinToString("  |  ").ifBlank { "Waiting for identity data" }
}

data class PgnTrafficStat(
    val pgn: UInt,
    val frameCount: Int,
    val byteCount: Int,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
) {
    val averagePayloadBytes: Double
        get() = if (frameCount == 0) 0.0 else byteCount.toDouble() / frameCount
}
