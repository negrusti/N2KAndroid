package com.gregor.n2kandroid.nmea

class FastPacketAssembler {
    private val sessions = mutableMapOf<Key, Session>()

    fun process(frame: Nmea2000Frame): ByteArray? {
        if (frame.payload.isEmpty()) return null

        val header = frame.payload[0].toInt() and 0xFF
        val sequenceId = header shr 5
        val frameIndex = header and 0x1F
        val key = Key(frame.source, frame.pgn, sequenceId)

        if (frameIndex == 0) {
            if (frame.payload.size < 2) return null
            val totalLength = frame.payload[1].toInt() and 0xFF
            val session = Session(totalLength)
            appendBytes(session, frame.payload, startIndex = 2)
            sessions[key] = session
            return completeIfReady(key, session)
        }

        val session = sessions[key] ?: return null
        appendBytes(session, frame.payload, startIndex = 1)
        return completeIfReady(key, session)
    }

    fun clear() {
        sessions.clear()
    }

    private fun appendBytes(session: Session, payload: ByteArray, startIndex: Int) {
        for (index in startIndex until payload.size) {
            if (session.data.size >= session.totalLength) return
            session.data.add(payload[index])
        }
    }

    private fun completeIfReady(key: Key, session: Session): ByteArray? {
        if (session.data.size < session.totalLength) return null
        sessions.remove(key)
        return session.data.take(session.totalLength).toByteArray()
    }

    private data class Key(val source: Int, val pgn: UInt, val sequenceId: Int)

    private class Session(val totalLength: Int) {
        val data = ArrayList<Byte>(totalLength)
    }
}

