package com.god.compressor.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GodFormat {
    val MAGIC  = byteArrayOf(0x47, 0x4F, 0x44, 0x03)
    const val BLOCK_SIZE = 128 * 1024
    const val DEFAULT_ORDER = 5

    data class Entry(
        val name: String, val isDir: Boolean,
        val data: ByteArray, val mode: Int, val mtime: Long
    )

    private fun entropy(d: ByteArray): Double {
        if (d.size < 64) return 0.0
        val n = minOf(d.size, 8192)
        val c = IntArray(256)
        for (i in 0 until n) c[d[i].toInt() and 0xFF]++
        var e = 0.0
        for (v in c) if (v > 0) { val p = v.toDouble() / n; e -= p * Math.log(p) / Math.log(2.0) }
        return e
    }

    private fun compressBlockAuto(d: ByteArray, order: Int, progress: ((Int) -> Unit)?): Triple<Int, ByteArray, Int> {
        if (d.isEmpty()) return Triple(0, ByteArray(0), 0)
        if (d.size > 512 && entropy(d) > 7.85) return Triple(0, d, d.size)
        val lz = LZ77.compress(d, progress)
        var method = 0; var payload = d
        if (lz.size < d.size) { method = 1; payload = lz }
        if (d.size >= 4096 && payload.size > d.size * 0.35) {
            val ppm = PPMModel(order).compress(d, progress)
            if (ppm.size < payload.size) { method = 2; payload = ppm }
        }
        return Triple(method, payload, d.size)
    }

    private fun decompressBlock(method: Int, payload: ByteArray, origSize: Int, order: Int): ByteArray {
        return when (method) {
            0 -> payload.copyOf(origSize)
            1 -> LZ77.decompress(payload, origSize)
            2 -> PPMModel(order).decompress(payload, origSize)
            else -> throw IllegalArgumentException("metode blok $method")
        }
    }

    fun pack(entries: List<Entry>, order: Int = DEFAULT_ORDER, progress: ((Long, Long) -> Unit)? = null): ByteArray {
        val total = entries.filter { !it.isDir }.sumOf { it.data.size.toLong() }
        var done = 0L
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(bufI(entries.size))
        for (e in entries) {
            val nameB = e.name.toByteArray(Charsets.UTF_8)
            val blocks = if (e.isDir) emptyList() else e.data.toList().chunked(BLOCK_SIZE).map { it.toByteArray() }
            val blocksInfo = ArrayList<Triple<Int, ByteArray, Int>>()
            for (blk in blocks) {
                val (m, p, osz) = compressBlockAuto(blk, order) {
                    done += it; progress?.invoke(done, total)
                }
                blocksInfo.add(Triple(m, p, osz))
                done += blk.size; progress?.invoke(done, total)
            }
            out.write(bufS(nameB.size)); out.write(nameB)
            out.write(if (e.isDir) 1 else 0)
            out.write(bufQ(e.data.size.toLong()))
            out.write(bufI(e.mode))
            out.write(bufQ(e.mtime))
            out.write(bufI(blocksInfo.size))
            for ((m, p, osz) in blocksInfo) {
                out.write(m); out.write(bufI(p.size)); out.write(bufI(osz))
            }
            for ((_, p, _) in blocksInfo) out.write(p)
        }
        return out.toByteArray()
    }

    fun unpack(blob: ByteArray, order: Int = DEFAULT_ORDER, progress: ((Long, Long) -> Unit)? = null): List<Entry> {
        require(blob.size >= 8 && blob.copyOf(4).contentEquals(MAGIC)) { "Bukan .god v2.1" }
        val n = readI(blob, 4)
        var pos = 8
        val metas = ArrayList<Map<String, Any>>()
        for (i in 0 until n) {
            val nl = ((blob[pos].toInt() and 0xFF) shl 8) or (blob[pos + 1].toInt() and 0xFF); pos += 2
            val name = String(blob, pos, nl, Charsets.UTF_8); pos += nl
            val flag = blob[pos].toInt() and 0xFF; pos++
            val size = readQ(blob, pos); pos += 8
            val mode = readI(blob, pos); pos += 4
            val mtime = readQ(blob, pos); pos += 8
            val nb = readI(blob, pos); pos += 4
            val blocks = ArrayList<Triple<Int, Int, Int>>()
            for (j in 0 until nb) {
                val m = blob[pos].toInt() and 0xFF; pos++
                val csz = readI(blob, pos); pos += 4
                val osz = readI(blob, pos); pos += 4
                blocks.add(Triple(m, csz, osz))
            }
            metas.add(mapOf("name" to name, "flag" to flag, "size" to size,
                            "mode" to mode, "mtime" to mtime, "blocks" to blocks))
        }
        val total = metas.filter { (it["flag"] as Int) == 0 }.sumOf { (it["size"] as Long) }
        var done = 0L
        val entries = ArrayList<Entry>()
        for (meta in metas) {
            @Suppress("UNCHECKED_CAST") val blocks = meta["blocks"] as List<Triple<Int, Int, Int>>
            val name = meta["name"] as String; val flag = meta["flag"] as Int
            val mode = meta["mode"] as Int; val mtime = meta["mtime"] as Long
            if (flag == 1) { entries.add(Entry(name, true, ByteArray(0), mode, mtime)); continue }
            val data = ByteArrayOutputStream()
            for ((m, csz, osz) in blocks) {
                val payload = blob.copyOfRange(pos, pos + csz); pos += csz
                data.write(decompressBlock(m, payload, osz, order))
                done += osz; progress?.invoke(done, total)
            }
            entries.add(Entry(name, false, data.toByteArray(), mode, mtime))
        }
        return entries
    }

    fun info(blob: ByteArray): Map<String, Any> {
        require(blob.size >= 8 && blob.copyOf(4).contentEquals(MAGIC)) { "Bukan .god v2.1" }
        val n = readI(blob, 4)
        var pos = 8; var total = 0L
        for (i in 0 until n) {
            val nl = ((blob[pos].toInt() and 0xFF) shl 8) or (blob[pos + 1].toInt() and 0xFF); pos += 2 + nl
            val flag = blob[pos].toInt() and 0xFF; pos++
            val size = readQ(blob, pos); pos += 8
            pos += 4; pos += 8
            val nb = readI(blob, pos); pos += 4 + nb * 9
            if (flag == 0) total += size
        }
        return mapOf("entries" to n, "orig" to total, "comp" to blob.size)
    }

    private fun bufI(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array()
    private fun bufS(v: Int) = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
    private fun bufQ(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(v).array()
    private fun readI(b: ByteArray, p: Int) = ByteBuffer.wrap(b, p, 4).order(ByteOrder.BIG_ENDIAN).int
    private fun readQ(b: ByteArray, p: Int) = ByteBuffer.wrap(b, p, 8).order(ByteOrder.BIG_ENDIAN).long
}
