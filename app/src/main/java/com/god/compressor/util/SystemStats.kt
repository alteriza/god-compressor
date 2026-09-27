package com.god.compressor.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import java.io.RandomAccessFile

data class SystemSnapshot(
    val cpuPercent: Float,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val appRamMb: Long,
    val threads: Int,
    val cores: Int,
    val uptime: Long
)

object SystemStats {
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L

    fun snapshot(ctx: Context): SystemSnapshot {
        val cores = Runtime.getRuntime().availableProcessors()
        var cpuPct = 0f
        try {
            val f = RandomAccessFile("/proc/stat", "r")
            val line = f.readLine(); f.close()
            val parts = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            var total = 0L
            for (i in 1 until parts.size) total += parts[i].toLong()
            val idle = parts[4].toLong()
            if (lastCpuTotal != 0L) {
                val dTot = total - lastCpuTotal
                val dIdle = idle - lastCpuIdle
                if (dTot > 0) cpuPct = (100f * (dTot - dIdle) / dTot).coerceIn(0f, 100f)
            }
            lastCpuTotal = total; lastCpuIdle = idle
        } catch (_: Exception) {}

        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalMb = mi.totalMem / (1024 * 1024)
        val usedMb  = (mi.totalMem - mi.availMem) / (1024 * 1024)

        val dbg = Debug.MemoryInfo()
        Debug.getMemoryInfo(dbg)
        val appMb = dbg.totalPss / 1024L

        return SystemSnapshot(
            cpuPercent = cpuPct,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            appRamMb = appMb,
            threads = Thread.activeCount(),
            cores = cores,
            uptime = android.os.SystemClock.elapsedRealtime() / 1000
        )
    }
}
