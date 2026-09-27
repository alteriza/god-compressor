package com.god.compressor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.god.compressor.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val snap by vm.snap.collectAsState()
    val status by vm.status.collectAsState()
    val progress by vm.progress.collectAsState()
    val result by vm.result.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            try { vm.refreshStats(ctx) } catch (_: Throwable) {}
            delay(1000)
        }
    }

    val pickCompress = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.compress(ctx, it) } }

    val pickExtract = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.extract(ctx, it) } }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { vm.saveResult(ctx, it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("GOD Compressor v2.3") }) }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsCard(snap)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kompresi", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { pickCompress.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Pilih file → Kompres .god")
                    }
                    OutlinedButton(onClick = { pickExtract.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Pilih .god → Ekstrak")
                    }
                }
            }

            // Progress bar: sanitasi dulu, jangan pernah kirim NaN/Inf ke Compose
            val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
            if (safeProgress > 0.005f && safeProgress < 0.995f) {
                LinearProgressIndicator(
                    progress = safeProgress,
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
            }

            if (status.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Status", style = MaterialTheme.typography.titleSmall)
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            result?.let { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Hasil siap disimpan", style = MaterialTheme.typography.titleSmall)
                        Text("Nama: ${r.suggestedName}")
                        Text("Ukuran: ${r.data.size / 1024} KB")
                        Button(onClick = { saveFile.launch(r.suggestedName) },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Simpan ke penyimpanan")
                        }
                    }
                }
            }
        }
    }
}