package com.god.compressor.core

class PPMModel(private val maxOrder: Int) {
    private val ctx = Array(maxOrder + 1) { HashMap<Long, HashMap<Int, Int>>() }
    private val rescale = 60000

    private fun key(hist: ByteArray, k: Int): Long {
        if (k == 0) return 0L
        if (k > hist.size) return -1L
        var x = 0L
        val start = hist.size - k
        for (i in start until start + k) x = (x shl 8) or (hist[i].toLong() and 0xFF)
        return x
    }

    fun encode(enc: RangeEncoder, hist: ByteArray, sym: Int) {
        for (k in maxOrder downTo 0) {
            val kk = key(hist, k); if (kk < 0) continue
            val d = ctx[k][kk] ?: continue
            val esc = d.size
            var total = esc; for (v in d.values) total += v
            if (d.containsKey(sym)) {
                var cl = esc
                for ((s, f) in d) { if (s == sym) break; cl += f }
                enc.encode(cl, cl + d[sym]!!, total)
                update(hist, sym); return
            } else {
                enc.encode(0, esc, total)
            }
        }
        enc.encode(sym, sym + 1, 256)
        update(hist, sym)
    }

    fun decode(dec: RangeDecoder, hist: ByteArray): Int {
        for (k in maxOrder downTo 0) {
            val kk = key(hist, k); if (kk < 0) continue
            val d = ctx[k][kk] ?: continue
            val esc = d.size
            var total = esc; for (v in d.values) total += v
            val cum = dec.getFreq(total)
            if (cum < esc) { dec.decode(0, esc, total); continue }
            var acc = esc; var chosen: Int? = null
            for ((s, f) in d) {
                if (cum < acc + f) { dec.decode(acc, acc + f, total); chosen = s; break }
                acc += f
            }
            val c = chosen ?: throw IllegalStateException("PPM corrupt")
            update(hist, c); return c
        }
        val cum = dec.getFreq(256).toInt()
        dec.decode(cum, cum + 1, 256)
        update(hist, cum); return cum
    }

    private fun update(hist: ByteArray, sym: Int) {
        for (k in 0..maxOrder) {
            if (k > hist.size) continue
            val kk = key(hist, k)
            val d = ctx[k].getOrPut(kk) { HashMap() }
            d[sym] = (d[sym] ?: 0) + 1
            var sum = 0; for (v in d.values) sum += v
            if (sum > rescale) for (key in d.keys) d[key] = (d[key]!! + 1) shr 1
        }
    }

    fun compress(data: ByteArray, progress: ((Int) -> Unit)? = null): ByteArray {
        val enc = RangeEncoder()
        val hb = ByteArray(data.size)
        var hlen = 0
        data.forEachIndexed { i, b ->
            val s = b.toInt() and 0xFF
            encode(enc, hb.copyOf(hlen), s)
            hb[hlen++] = b
            if (progress != null && i % 2048 == 0) progress(i)
        }
        progress?.invoke(data.size)
        return enc.finish()
    }

    fun decompress(blob: ByteArray, outSize: Int, progress: ((Int) -> Unit)? = null): ByteArray {
        val dec = RangeDecoder(blob)
        val out = ByteArray(outSize)
        for (i in 0 until outSize) {
            val hb = if (i == 0) ByteArray(0) else out.copyOf(i)
            out[i] = decode(dec, hb).toByte()
            if (progress != null && i % 2048 == 0) progress(i)
        }
        progress?.invoke(outSize)
        return out
    }
}
