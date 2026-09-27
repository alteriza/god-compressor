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

        StatBar("CPU", s.cpuPercent / 100f, "${"%.1f".format(s.cpuPercent)}%  (${s.cores} core)")
        Spacer(Modifier.height(6.dp))
        StatBar("RAM sistem", s.ramUsedMb.toFloat() / s.ramTotalMb,
                "${s.ramUsedMb} / ${s.ramTotalMb} MB")
        Spacer(Modifier.height(6.dp))
        StatBar("RAM app", (s.appRamMb.toFloat() / s.ramTotalMb).coerceIn(0f, 1f),
                "${s.appRamMb} MB")
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Threads: ${s.threads}", style = MaterialTheme.typography.bodySmall)
            Text("Uptime: ${s.uptime}s", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatBar(label: String, fraction: Float, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = fraction.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = when {
                fraction > 0.85f -> Color(0xFFE53935)
                fraction > 0.6f -> Color(0xFFFB8C00)
                else -> MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surface
        )
    }
}
