package com.god.compressor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.god.compressor.util.SystemSnapshot

/** Kembalikan Float yang 100% aman (bukan NaN/Inf, dalam [0,1]). */
private fun safeFraction(value: Float): Float {
    if (!value.isFinite()) return 0f
    return value.coerceIn(0f, 1f)
}

/** Hitung rasio used/total dengan aman, hindari div-by-zero. */
private fun safeRatio(used: Long, total: Long): Float {
    if (total <= 0L) return 0f
    val v = used.toFloat() / total.toFloat()
    return safeFraction(v)
}

@Composable
fun StatsCard(s: SystemSnapshot) {
    Column(
        Modifier.fillMaxWidth().padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text("System Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (s.cpuAvailable) {
            StatBar(
                "CPU",
                safeFraction(s.cpuPercent / 100f),
                "${"%.1f".format(s.cpuPercent)}%  (${s.cores} core)"
            )
        } else {
            StatBar("CPU", 0f, "N/A (perlu akses sistem)")
        }
        Spacer(Modifier.height(6.dp))

        if (s.ramAvailable && s.ramTotalMb > 0L) {
            StatBar(
                "RAM sistem",
                safeRatio(s.ramUsedMb, s.ramTotalMb),
                "${s.ramUsedMb} / ${s.ramTotalMb} MB"
            )
        } else {
            StatBar("RAM sistem", 0f, "N/A")
        }
        Spacer(Modifier.height(6.dp))

        if (s.ramTotalMb > 0L) {
            StatBar("RAM app", safeRatio(s.appRamMb, s.ramTotalMb), "${s.appRamMb} MB")
        } else {
            StatBar("RAM app", 0f, "${s.appRamMb} MB")
        }
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Threads: ${s.threads}", style = MaterialTheme.typography.bodySmall)
            Text("Uptime: ${s.uptime}s", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatBar(label: String, fraction: Float, value: String) {
    val f = safeFraction(fraction)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = f,
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = when {
                f > 0.85f -> Color(0xFFE53935)
                f > 0.6f  -> Color(0xFFFFA000)
                else       -> MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surface
        )
    }
}