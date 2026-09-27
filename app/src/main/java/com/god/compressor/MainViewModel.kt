package com.god.compressor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.god.compressor.core.GodFormat
import com.god.compressor.util.FileUtil
import com.god.compressor.util.SystemStats
import com.god.compressor.util.SystemSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SaveResult(val suggestedName: String, val data: ByteArray)

class MainViewModel : ViewModel() {
    private val _snap = MutableStateFlow(SystemSnapshot(0f,0,0,0,0,0,0))
    val snap = _snap.asStateFlow()

    private val _status = MutableStateFlow("Siap.")
    val status = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _result = MutableStateFlow<SaveResult?>(null)
    val result = _result.asStateFlow()

    fun refreshStats(ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _snap.value = SystemStats.snapshot(ctx)
        }
    }

    fun compress(ctx: Context, uri: Uri) = viewModelScope.launch {
        _result.value = null
        try {
            _status.value = "Membaca file..."
            val name = FileUtil.displayName(ctx, uri)
            val data = withContext(Dispatchers.IO) { FileUtil.readBytes(ctx, uri) }
            _status.value = "Mengompres ${name} (${data.size / 1024} KB)..."
            val entry = GodFormat.Entry(name, false, data, 0o644, System.currentTimeMillis() / 1000)
            val blob = withContext(Dispatchers.Default) {
                GodFormat.pack(listOf(entry), progress = { p, t ->
                    _progress.value = if (t > 0) p.toFloat() / t else 0f
                })
            }
            val ratio = data.size.toDouble() / blob.size
            val save = 100 - blob.size * 100.0 / data.size
            _status.value = "✔ ${data.size / 1024} KB → ${blob.size / 1024} KB  " +
                            "(${"%.2f".format(ratio)}x, hemat ${"%.1f".format(save)}%)"
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            _result.value = SaveResult("${name}_$ts.god", blob)
            _progress.value = 0f
        } catch (e: Exception) {
            _status.value = "✘ Gagal: ${e.message}"
        }
    }

    fun extract(ctx: Context, uri: Uri) = viewModelScope.launch {
        _result.value = null
        try {
            _status.value = "Membaca arsip .god..."
            val blob = withContext(Dispatchers.IO) { FileUtil.readBytes(ctx, uri) }
            val entries = withContext(Dispatchers.Default) {
                GodFormat.unpack(blob, progress = { p, t ->
                    _progress.value = if (t > 0) p.toFloat() / t else 0f
                })
            }
            val lines = entries.joinToString("\n") { e ->
                if (e.isDir) "  📁 ${e.name}/" else "  📄 ${e.name}  (${e.data.size / 1024} KB)"
            }
            _status.value = "✔ ${entries.size} entri dari arsip:\n$lines"
            val first = entries.firstOrNull { !it.isDir }
            if (first != null) {
                _result.value = SaveResult(first.name, first.data)
            }
            _progress.value = 0f
        } catch (e: Exception) {
            _status.value = "✘ Gagal: ${e.message}"
        }
    }

    fun saveResult(ctx: Context, uri: Uri) = viewModelScope.launch {
        try {
            val r = _result.value ?: return@launch
            withContext(Dispatchers.IO) { FileUtil.writeBytes(ctx, uri, r.data) }
            _status.value = "✔ Tersimpan: ${r.suggestedName}"
            _result.value = null
        } catch (e: Exception) {
            _status.value = "✘ Gagal simpan: ${e.message}"
        }
    }
}
