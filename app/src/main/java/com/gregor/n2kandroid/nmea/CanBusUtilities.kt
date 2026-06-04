package com.gregor.n2kandroid.nmea

object CanBusUtilities {
    fun buildCanId(pgn: UInt, destination: Int, source: Int, priority: Int): UInt {
        var pgnField = pgn
        val pf = ((pgnField shr 8) and 0xFFu).toInt()
        if (pf < 0xF0) {
            pgnField = pgnField or (destination and 0xFF).toUInt()
        }

        return ((priority.coerceIn(0, 7).toUInt()) shl 26) or
            (pgnField shl 8) or
            (source and 0xFF).toUInt()
    }

    fun parseCanId(canId: UInt, timestampMillis: Long, payload: ByteArray): Nmea2000Frame {
        val priority = ((canId shr 26) and 0x7u).toInt()
        val source = (canId and 0xFFu).toInt()
        var pgn = (canId shr 8) and 0x3FFFFu
        var destination = 255
        val pf = ((pgn shr 8) and 0xFFu).toInt()

        if (pf < 0xF0) {
            destination = (pgn and 0xFFu).toInt()
            pgn = pgn and 0x3FF00u
        }

        return Nmea2000Frame(
            timestampMillis = timestampMillis,
            canId = canId,
            pgn = pgn,
            priority = priority,
            source = source,
            destination = destination,
            payload = payload,
        )
    }
}

