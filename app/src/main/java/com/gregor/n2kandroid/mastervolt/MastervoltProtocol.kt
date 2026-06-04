package com.gregor.n2kandroid.mastervolt

import com.gregor.n2kandroid.nmea.CanBusUtilities
import com.gregor.n2kandroid.nmea.Nmea2000Frame

object MastervoltProtocol {
    const val VENDOR_ID = 0x1A64
    const val PRODUCT_ID = 0x0000
    const val HID_PAYLOAD_SIZE = 64
    const val HID_REPORT_BUFFER_SIZE = 65
    const val MAX_PACKETS_PER_REPORT = 4
    const val PACKET_SIZE = 14

    private val ISO_REQUEST_PGN = 59904u
    private val PRODUCT_INFORMATION_PGN = 126996u
    private const val BROADCAST_ADDRESS = 255
    private const val REQUEST_SOURCE_ADDRESS = 254
    private const val REQUEST_PRIORITY = 6

    fun decodeReport(reportBuffer: ByteArray, bytesRead: Int, nowMillis: Long): List<Nmea2000Frame> {
        val payloadOffset = getPayloadOffset(reportBuffer, bytesRead)
        if (payloadOffset < 0) return emptyList()

        val count = reportBuffer[payloadOffset].toInt() and 0xFF
        if (count > MAX_PACKETS_PER_REPORT) return emptyList()

        val frames = ArrayList<Nmea2000Frame>(count)
        for (slot in 0 until count) {
            val packetOffset = payloadOffset + 1 + (PACKET_SIZE * slot)
            val metaLowOffset = payloadOffset + 60 + slot
            if (packetOffset + PACKET_SIZE > bytesRead || metaLowOffset >= bytesRead) {
                return frames
            }

            decodeFrame(reportBuffer, packetOffset, nowMillis)?.let(frames::add)
        }

        return frames
    }

    fun encodeFrameReport(canId: UInt, payload: ByteArray, includeReportId: Boolean): ByteArray {
        require(payload.size <= 8) { "CAN payloads cannot exceed 8 bytes." }

        val size = if (includeReportId) HID_REPORT_BUFFER_SIZE else HID_PAYLOAD_SIZE
        val report = ByteArray(size)
        val payloadOffset = if (includeReportId) 1 else 0
        report[payloadOffset] = 1
        packCanFrame(report, payloadOffset + 1, canId, payload)
        return report
    }

    fun encodeProductInformationRequest(includeReportId: Boolean): ByteArray {
        val canId = CanBusUtilities.buildCanId(
            pgn = ISO_REQUEST_PGN,
            destination = BROADCAST_ADDRESS,
            source = REQUEST_SOURCE_ADDRESS,
            priority = REQUEST_PRIORITY,
        )
        val payload = byteArrayOf(
            (PRODUCT_INFORMATION_PGN and 0xFFu).toByte(),
            ((PRODUCT_INFORMATION_PGN shr 8) and 0xFFu).toByte(),
            ((PRODUCT_INFORMATION_PGN shr 16) and 0xFFu).toByte(),
        )
        return encodeFrameReport(canId, payload, includeReportId)
    }

    private fun decodeFrame(packet: ByteArray, offset: Int, nowMillis: Long): Nmea2000Frame? {
        val canId = unpackCanId(packet, offset)
        val dlc = packet[offset + 4].toInt() and 0x0F
        if (dlc > 8) return null

        val payload = ByteArray(dlc)
        packet.copyInto(payload, destinationOffset = 0, startIndex = offset + 5, endIndex = offset + 5 + dlc)
        return CanBusUtilities.parseCanId(canId, nowMillis, payload)
    }

    private fun getPayloadOffset(reportBuffer: ByteArray, bytesRead: Int): Int {
        if (bytesRead >= HID_REPORT_BUFFER_SIZE &&
            reportBuffer[0].toInt() == 0 &&
            (reportBuffer[1].toInt() and 0xFF) <= MAX_PACKETS_PER_REPORT
        ) {
            return 1
        }

        if (bytesRead >= HID_PAYLOAD_SIZE &&
            (reportBuffer[0].toInt() and 0xFF) <= MAX_PACKETS_PER_REPORT
        ) {
            return 0
        }

        if (bytesRead >= HID_REPORT_BUFFER_SIZE &&
            (reportBuffer[1].toInt() and 0xFF) <= MAX_PACKETS_PER_REPORT
        ) {
            return 1
        }

        return -1
    }

    private fun unpackCanId(packet: ByteArray, offset: Int): UInt {
        val b0 = packet[offset].toInt() and 0xFF
        val b1 = packet[offset + 1].toInt() and 0xFF
        val b2 = packet[offset + 2].toInt() and 0xFF
        val b3 = packet[offset + 3].toInt() and 0xFF
        val top5 = ((((b0 shl 8) or b1) shr 5) and 0x1F).toUInt()
        val low18 = (((b1 and 0x03) shl 16) or (b2 shl 8) or b3).toUInt()
        val low23 = (top5 shl 18) or low18
        return ((b0 shr 2).toUInt() shl 23) or low23
    }

    private fun packCanFrame(report: ByteArray, packetOffset: Int, canId: UInt, payload: ByteArray) {
        val type = ((canId shr 23) and 0x3Fu).toInt()
        val low23 = canId and 0x7FFFFFu

        report[packetOffset] = (type shl 2).toByte()
        packLow23(report, packetOffset, low23)
        report[packetOffset + 4] = (payload.size and 0x0F).toByte()

        for (index in payload.indices) {
            report[packetOffset + 5 + index] = payload[index]
        }
    }

    private fun packLow23(report: ByteArray, packetOffset: Int, low23: UInt) {
        report[packetOffset] = (((report[packetOffset].toInt() and 0xFC) or
            ((low23 shr 21) and 0x03u).toInt())).toByte()
        report[packetOffset + 1] = ((((low23 shr 13) and 0xE0u).toInt()) or
            (report[packetOffset + 1].toInt() and 0x1C) or
            ((low23 shr 16) and 0x03u).toInt()).toByte()
        report[packetOffset + 2] = ((low23 shr 8) and 0xFFu).toByte()
        report[packetOffset + 3] = (low23 and 0xFFu).toByte()
    }
}
