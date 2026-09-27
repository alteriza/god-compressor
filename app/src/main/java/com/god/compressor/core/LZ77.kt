package com.god.compressor.core

import java.io.ByteArrayOutputStream

object LZ77 {
    private const val MIN_MATCH = 3
    private const val MAX_MATCH = 258
    private const val WINDOW = 65535
    private const val MAX_CHAIN = 64
    private const val HASH_LIMIT = 256

    class BitWriter {
        private val buf = ByteArrayOutputStream()
        private var cur = 0; private var bits = 0
        fun bit(b: Int) {
            cur = (cur shl 1) or (b and 1); bits++
            if (bits == 8) { buf.write(cur); cur = 0; bits = 0 }
        }
        fun bitsN(v: Int, n: Int) { for (i in n - 1 downTo 0) bit((v ushr i) and 1) }
        fun flush(): ByteArray {
            if (bits > 0) { cur = cur shl (8 - bits); buf.write(cur); cur = 0; bits = 0 }
            return buf.toByteArray()
        }
    }

    class BitReader(private val data: ByteArray) {
        private var pos = 0; private var cur = 0; private var bits = 0
        fun bit(): Int {
            if (bits == 0) {
                if (pos >= data.size) return -1
                cur = data[pos++].toInt() and 0xFF; bits = 8
            }
            bits--; return (cur ushr bits) and 1
        }
        fun bitsN(n: Int): Int {
            var v = 0
            repeat(n) { val b = bit(); if (b < 0) return -1; v = (v shl 1) or b }
            return v
        }
    }

    private fun wvar(bw: BitWriter, v: Int) {
        val x = v + 1; val n = 32 - Integer.numberOfLeadingZeros(x)
        repeat(n - 1) { bw.bit(0) }
        bw.bitsN(x, n)
    }
    private fun rvar(br: BitReader): Int {
        var z = 0
        while (true) { val b = br.bit(); if (b < 0) return -1; if (b == 1) break; z++ }
        var v = 1
        repeat(z) { val b = br.bit(); if (b < 0) return -1; v = (v shl 1) or b }
        return v - 1
    }

    private fun hash3(d: ByteArray, p: Int) =
        ((d[p].toInt() and 0xFF) shl 16) or
        ((d[p + 1].toInt() and 0xFF) shl 8) or
        (d[p + 2].toInt() and 0xFF)

    fun compress(data: ByteArray, progress: ((Int) -> Unit)? = null): ByteArray {
        val n = data.size; val bw = BitWriter(); var pos = 0
        val chains = HashMap<Int, ArrayDeque<Int>>()
        while (pos < n) {
            var bestLen = 0; var bestDist = 0
            if (pos + MIN_MATCH <= n) {
                val key = hash3(data, pos)
                val chain = chains[key]
                if (chain != null) {
                    var count = 0; val maxMl = minOf(MAX_MATCH, n - pos)
                    for (cp in chain.reversed()) {
                        val dist = pos - cp
                        if (dist > WINDOW) break
                        var ml = 0
                        while (ml < maxMl && data[cp + ml] == data[pos + ml]) ml++
                        if (ml > bestLen) { bestLen = ml; bestDist = dist; if (ml == maxMl) break }
                        if (++count >= MAX_CHAIN) break
                    }
                }
            }
            if (bestLen >= MIN_MATCH) {
                bw.bit(1); wvar(bw, bestLen - MIN_MATCH); wvar(bw, bestDist - 1)
                val end = pos + bestLen
                while (pos < end) {
                    if (pos + MIN_MATCH <= n) {
                        chains.getOrPut(hash3(data, pos)) { ArrayDeque() }.let {
                            it.addLast(pos); if (it.size > HASH_LIMIT) it.removeFirst()
                        }
                    }
                    pos++
                }
            } else {
                bw.bit(0); bw.bitsN(data[pos].toInt() and 0xFF, 8)
                if (pos + MIN_MATCH <= n) {
                    chains.getOrPut(hash3(data, pos)) { ArrayDeque() }.let {
                        it.addLast(pos); if (it.size > HASH_LIMIT) it.removeFirst()
                    }
                }
                pos++
            }
            if (progress != null && pos % 4096 == 0) progress(pos)
        }
        progress?.invoke(n)
        return bw.flush()
    }

    fun decompress(blob: ByteArray, outSize: Int): ByteArray {
        val br = BitReader(blob); val out = ByteArrayOutputStream()
        while (out.size() < outSize) {
            val flag = br.bit(); if (flag < 0) break
            if (flag == 0) {
                val b = br.bitsN(8); if (b < 0) break; out.write(b)
            } else {
                val ml = rvar(br); val dm = rvar(br); if (ml < 0 || dm < 0) break
                val length = ml + MIN_MATCH; val dist = dm + 1
                val buf = out.toByteArray()
                val start = buf.size - dist
                if (start < 0) throw IllegalStateException("korup")
                for (i in 0 until length) out.write(buf[start + i].toInt() and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
