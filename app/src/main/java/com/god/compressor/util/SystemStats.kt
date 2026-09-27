package com.god.compressor.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import java.io.RandomAccessFile

data class SystemSnapshot(
    val cpuPercent: Float,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val appRamMb: Long,
    val threads: Int,
    val cores: Int,
    val uptime: Long,
    val cpuAvailable: Boolean = true,
    val ramAvailable: Boolean = true
)

object SystemStats {
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L
    @Volatile private var cpuReadable = true

    fun snapshot(ctx: Context): SystemSnapshot {
        val cores = try {
            Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        } catch (_: Throwable) { 1 }

        // ---- CPU (bisa gagal karena SELinux di Android 8+)
        var cpuPct = 0f
        if (cpuReadable) {
            var raf: RandomAccessFile? = null
            try {
                raf = RandomAccessFile("/proc/stat", "r")
                val line = raf.readLine()
                if (line != null && line.startsWith("cpu ")) {
                    val parts = line.substring(4).trim().split(Regex("\\s+"))
                    if (parts.size >= 5) {
                        var total = 0L
                        var idle = 0L
                        for ((i, p) in parts.withIndex()) {
                            val v = p.toLongOrNull() ?: 0L
                            total += v
                            if (i == 3 || i == 4) idle += v
                        }
                        if (lastCpuTotal != 0L && total > lastCpuTotal) {
                            val dTot = total - lastCpuTotal
                            val dIdle = idle - lastCpuIdle
                            if (dTot > 0L) {
                                cpuPct = (100f * (dTot - dIdle) / dTot).coerceIn(0f, 100f)
                            }
                        }
                        lastCpuTotal = total
                        lastCpuIdle = idle
                    }
                }
            } catch (_: Throwable) {
                cpuReadable = false // fallback: jangan coba lagi
            } finally {
                try { raf?.close() } catch (_: Throwable) {}
            }
        }

        // ---- RAM sistem
        var ramTotalMb = 0L
        var ramUsedMb = 0L
        var ramOk = false
        try {
            val am = ctx.applicationContext
                .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                ramTotalMb = mi.totalMem / (1024L * 1024L)
                ramUsedMb = (mi.totalMem - mi.availMem) / (1024L * 1024L)
                ramOk = ramTotalMb > 0L
            }
        } catch (_: Throwable) {}

        // ---- RAM app
        var appMb = 0L
        try {
            val dbg = Debug.MemoryInfo()
            Debug.getMemoryInfo(dbg)
            appMb = dbg.totalPss / 1024L
        } catch (_: Throwable) {}

        val threads = try { Thread.activeCount() } catch (_: Throwable) { 0 }
        val uptime = try { SystemClock.elapsedRealtime() / 1000 } catch (_: Throwable) { 0L }

        return SystemSnapshot(
            cpuPercent = if (cpuPct.isFinite()) cpuPct else 0f,
            ramUsedMb = ramUsedMb,
            ramTotalMb = ramTotalMb,
            appRamMb = appMb,
            threads = threads,
            cores = cores,
            uptime = uptime,
            cpuAvailable = cpuReadable,
            ramAvailable = ramOk
        )
    }
}