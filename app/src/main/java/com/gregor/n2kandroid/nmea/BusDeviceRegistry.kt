package com.gregor.n2kandroid.nmea

class BusDeviceRegistry {
    private val devices = linkedMapOf<Int, DiscoveredDevice>()

    fun updateFromFrame(frame: Nmea2000Frame): List<DiscoveredDevice> {
        val current = devices[frame.source] ?: DiscoveredDevice(address = frame.source)
        devices[frame.source] = current.copy(
            lastSeenMillis = frame.timestampMillis,
            frameCount = current.frameCount + 1,
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
            )
        }
        return snapshot()
    }

    fun clear() {
        devices.clear()
    }

    fun snapshot(): List<DiscoveredDevice> {
        return devices.values.sortedBy { it.address }
    }
}

