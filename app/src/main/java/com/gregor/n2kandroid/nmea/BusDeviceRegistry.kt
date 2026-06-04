package com.gregor.n2kandroid.nmea

class BusDeviceRegistry {
    private val devices = linkedMapOf<Int, DiscoveredDevice>()
    private val statsByAddress = linkedMapOf<Int, LinkedHashMap<UInt, PgnTrafficStat>>()

    fun updateFromFrame(frame: Nmea2000Frame): List<DiscoveredDevice> {
        val current = devices[frame.source] ?: DiscoveredDevice(address = frame.source)
        val stats = updateStats(frame)
        devices[frame.source] = current.copy(
            lastSeenMillis = frame.timestampMillis,
            frameCount = current.frameCount + 1,
            pgnStats = stats,
        )
        return snapshot()
    }

    fun merge(update: DiscoveredDevice): List<DiscoveredDevice> {
        val current = devices[update.address]
        devices[update.address] = if (current == null) {
            update
        } else {
            current.copy(
                name = update.name ?: current.name,
                uniqueNumber = update.uniqueNumber ?: current.uniqueNumber,
                manufacturerCode = update.manufacturerCode ?: current.manufacturerCode,
                deviceFunction = update.deviceFunction ?: current.deviceFunction,
                deviceClass = update.deviceClass ?: current.deviceClass,
                industryGroup = update.industryGroup ?: current.industryGroup,
                productCode = update.productCode ?: current.productCode,
                modelId = update.modelId ?: current.modelId,
                softwareVersion = update.softwareVersion ?: current.softwareVersion,
                modelVersion = update.modelVersion ?: current.modelVersion,
                serialCode = update.serialCode ?: current.serialCode,
                nmeaVersion = update.nmeaVersion ?: current.nmeaVersion,
                loadEquivalency = update.loadEquivalency ?: current.loadEquivalency,
                lastSeenMillis = maxOf(current.lastSeenMillis, update.lastSeenMillis),
                pgnStats = statsByAddress[update.address]?.values?.sortedByDescending { it.frameCount } ?: current.pgnStats,
            )
        }
        return snapshot()
    }

    fun clear() {
        devices.clear()
        statsByAddress.clear()
    }

    fun snapshot(): List<DiscoveredDevice> {
        return devices.values.sortedBy { it.address }
    }

    private fun updateStats(frame: Nmea2000Frame): List<PgnTrafficStat> {
        val byPgn = statsByAddress.getOrPut(frame.source) { linkedMapOf() }
        val current = byPgn[frame.pgn]
        byPgn[frame.pgn] = if (current == null) {
            PgnTrafficStat(
                pgn = frame.pgn,
                frameCount = 1,
                byteCount = frame.payload.size,
                firstSeenMillis = frame.timestampMillis,
                lastSeenMillis = frame.timestampMillis,
            )
        } else {
            current.copy(
                frameCount = current.frameCount + 1,
                byteCount = current.byteCount + frame.payload.size,
                lastSeenMillis = frame.timestampMillis,
            )
        }
        return byPgn.values.sortedWith(compareByDescending<PgnTrafficStat> { it.frameCount }.thenBy { it.pgn })
    }
}
