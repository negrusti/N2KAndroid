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
        if (payload.size < 4) return null

        var offset = 0
        val nmeaVersionRaw = readUInt16(payload, offset)
        offset += 2
        val productCode = readUInt16(payload, offset)
        offset += 2

        val modelId = readLauString(payload, offset).also { offset = it.nextOffset }.value
        val softwareVersion = readLauString(payload, offset).also { offset = it.nextOffset }.value
        val modelVersion = readLauString(payload, offset).also { offset = it.nextOffset }.value
        val serialCode = readLauString(payload, offset).also { offset = it.nextOffset }.value

        val loadEquivalency = if (offset + 1 < payload.size) payload[offset + 1].toInt() and 0xFF else null

        return DiscoveredDevice(
            address = address,
            productCode = productCode,
            modelId = modelId,
            softwareVersion = softwareVersion,
            modelVersion = modelVersion,
            serialCode = serialCode,
            nmeaVersion = formatNmeaVersion(nmeaVersionRaw),
            loadEquivalency = loadEquivalency,
            lastSeenMillis = nowMillis,
        )
    }

    private fun readUInt16(payload: ByteArray, offset: Int): Int {
        return (payload[offset].toInt() and 0xFF) or ((payload[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLauString(payload: ByteArray, offset: Int): LauString {
        if (offset >= payload.size) return LauString(null, offset)

        val length = payload[offset].toInt() and 0xFF
        if (length == 0 || length == 0xFF) return LauString(null, offset + 1)
        if (offset + length > payload.size || length < 2) return LauString(null, payload.size)

        val encoding = payload[offset + 1].toInt() and 0xFF
        val bytes = payload.copyOfRange(offset + 2, offset + length)
        val value = when (encoding) {
            0, 1 -> bytes.toString(StandardCharsets.UTF_8)
            else -> bytes.toString(StandardCharsets.ISO_8859_1)
        }.trimEnd('\u0000', ' ')

        return LauString(value.ifBlank { null }, offset + length)
    }

    private fun formatNmeaVersion(raw: Int): String {
        return if (raw == 0 || raw == 0xFFFF) {
            ""
        } else {
            "%.3f".format(Locale.US, raw / 1000.0)
        }
    }

    private data class LauString(val value: String?, val nextOffset: Int)
}
