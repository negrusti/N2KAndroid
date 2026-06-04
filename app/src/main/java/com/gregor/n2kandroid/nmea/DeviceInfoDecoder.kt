package com.gregor.n2kandroid.nmea

import java.nio.charset.StandardCharsets
import java.util.Locale

object DeviceInfoDecoder {
    val ADDRESS_CLAIM_PGN = 60928u
    val PRODUCT_INFORMATION_PGN = 126996u

    fun decodeAddressClaim(address: Int, payload: ByteArray, nowMillis: Long): DiscoveredDevice? {
        if (payload.size < 8) return null

        var name = 0UL
        for (index in 0 until 8) {
            name = name or ((payload[index].toInt() and 0xFF).toULong() shl (index * 8))
        }

        return DiscoveredDevice(
            address = address,
            name = name,
            uniqueNumber = (name and 0x1FFFFFUL).toInt(),
            manufacturerCode = ((name shr 21) and 0x7FFUL).toInt(),
            deviceFunction = ((name shr 40) and 0xFFUL).toInt(),
            deviceClass = ((name shr 49) and 0x7FUL).toInt(),
            industryGroup = ((name shr 60) and 0x7UL).toInt(),
            lastSeenMillis = nowMillis,
        )
    }

    fun decodeProductInformation(address: Int, payload: ByteArray, nowMillis: Long): DiscoveredDevice? {
        if (payload.size < PRODUCT_INFO_MIN_LENGTH) return null

        val nmeaVersionRaw = readUInt16(payload, 0)
        val productCode = readUInt16(payload, 2)

        return DiscoveredDevice(
            address = address,
            productCode = productCode,
            modelId = readFixedString(payload, 4, PRODUCT_INFO_STRING_LENGTH),
            softwareVersion = readFixedString(payload, 36, PRODUCT_INFO_STRING_LENGTH),
            modelVersion = readFixedString(payload, 68, PRODUCT_INFO_STRING_LENGTH),
            serialCode = readFixedString(payload, 100, PRODUCT_INFO_STRING_LENGTH),
            nmeaVersion = formatNmeaVersion(nmeaVersionRaw),
            loadEquivalency = payload[133].toInt() and 0xFF,
            lastSeenMillis = nowMillis,
        )
    }

    private fun readUInt16(payload: ByteArray, offset: Int): Int {
        return (payload[offset].toInt() and 0xFF) or ((payload[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readFixedString(payload: ByteArray, offset: Int, length: Int): String? {
        if (offset + length > payload.size) return null
        val value = payload.copyOfRange(offset, offset + length)
            .toString(StandardCharsets.ISO_8859_1)
            .trimEnd('\u0000', '\u00FF', ' ')
            .trim()
        return value.ifBlank { null }
    }

    private fun formatNmeaVersion(raw: Int): String {
        return if (raw == 0 || raw == 0xFFFF) {
            ""
        } else {
            "%.3f".format(Locale.US, raw / 1000.0)
        }
    }

    private const val PRODUCT_INFO_STRING_LENGTH = 32
    private const val PRODUCT_INFO_MIN_LENGTH = 134
}
