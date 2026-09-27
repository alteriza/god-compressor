package com.god.compressor.core

import java.io.ByteArrayOutputStream

class RangeEncoder {
    private var low: Long = 0
    private var high: Long = 0xFFFFFFFFL
    private val out = ByteArrayOutputStream()

    fun encode(cl: Int, ch: Int, tot: Int) {
        val r = high - low + 1
        high = low + (r * ch) / tot - 1
        low  = low + (r * cl) / tot
        while ((low ushr 24) == (high ushr 24)) {
            out.write((low ushr 24).toInt() and 0xFF)
            low  = (low shl 8) and 0xFFFFFFFFL
            high = ((high shl 8) or 0xFF) and 0xFFFFFFFFL
        }
    }

    fun finish(): ByteArray {
        repeat(4) {
            out.write((low ushr 24).toInt() and 0xFF)
            low = (low shl 8) and 0xFFFFFFFFL
        }
        return out.toByteArray()
    }
}

class RangeDecoder(private val data: ByteArray) {
    private var pos = 0
    private var low: Long = 0
    private var high: Long = 0xFFFFFFFFL
    private var code: Long = 0

    init { repeat(4) { code = ((code shl 8) or next().toLong()) and 0xFFFFFFFFL } }

    private fun next(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else 0

    fun getFreq(tot: Int): Long {
        val r = high - low + 1
        return ((code - low + 1) * tot - 1) / r
    }

    fun decode(cl: Int, ch: Int, tot: Int) {
        val r = high - low + 1
        high = low + (r * ch) / tot - 1
        low  = low + (r * cl) / tot
        while ((low ushr 24) == (high ushr 24)) {
            low  = (low shl 8) and 0xFFFFFFFFL
            high = ((high shl 8) or 0xFF) and 0xFFFFFFFFL
            code = ((code shl 8) or next().toLong()) and 0xFFFFFFFFL
        }
    }
}
