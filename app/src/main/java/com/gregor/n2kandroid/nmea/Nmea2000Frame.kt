package com.gregor.n2kandroid.nmea

data class Nmea2000Frame(
    val timestampMillis: Long,
    val canId: UInt,
    val pgn: UInt,
    val priority: Int,
    val source: Int,
    val destination: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Nmea2000Frame) return false
        return timestampMillis == other.timestampMillis &&
            canId == other.canId &&
            pgn == other.pgn &&
            priority == other.priority &&
            source == other.source &&
            destination == other.destination &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = timestampMillis.hashCode()
        result = 31 * result + canId.hashCode()
        result = 31 * result + pgn.hashCode()
        result = 31 * result + priority
        result = 31 * result + source
        result = 31 * result + destination
        result = 31 * result + payload.contentHashCode()
        return result
    }

    fun summary(): String {
        val data = payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        return "PGN=$pgn src=$source dst=$destination pri=$priority data=$data"
    }
}

