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
    private val _snap = MutableStateFlow(SystemSnapshot(0f, 0, 0, 0, 0, 0, 0))
    val snap = _snap.asStateFlow()

    private val _status = MutableStateFlow("Siap.")
    val status = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _result = MutableStateFlow<SaveResult?>(null)
    val result = _result.asStateFlow()

    /** Set progress dengan sanitasi NaN/Inf dan clamp ke [0,1]. */
    private fun setProgress(done: Long, total: Long) {
        val v = if (total > 0L) (done.toDouble() / total.toDouble()).toFloat() else 0f
        _progress.value = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f
    }

    fun refreshStats(ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _snap.value = SystemStats.snapshot(ctx.applicationContext)
            } catch (_: Throwable) {
                // biarkan nilai lama, jangan crash
            }
        }
    }

    fun compress(ctx: Context, uri: Uri) = viewModelScope.launch {
        _result.value = null
        try {
            _status.value = "Membaca file..."
            val name = FileUtil.displayName(ctx, uri)
            val data = withContext(Dispatchers.IO) { FileUtil.readBytes(ctx, uri) }
            _status.value = "Mengompres ${name} (${data.size / 1024} KB)..."
            val entry = GodFormat.Entry(name, false, data, 420, System.currentTimeMillis() / 1000)
            val blob = withContext(Dispatchers.Default) {
                GodFormat.pack(listOf(entry), progress = { p, t -> setProgress(p, t) })
            }
            val ratio = if (blob.isNotEmpty()) data.size.toDouble() / blob.size else 0.0
            val save = if (data.isNotEmpty()) 100 - blob.size * 100.0 / data.size else 0.0
            _status.value = "✔ ${data.size / 1024} KB → ${blob.size / 1024} KB  " +
                            "(${"%.2f".format(ratio)}x, hemat ${"%.1f".format(save)}%)"
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            _result.value = SaveResult("${name}_$ts.god", blob)
            _progress.value = 0f
        } catch (t: Throwable) {
            _status.value = "✘ Gagal: ${t.message ?: t.javaClass.simpleName}"
            _progress.value = 0f
        }
    }

    fun extract(ctx: Context, uri: Uri) = viewModelScope.launch {
        _result.value = null
        try {
            _status.value = "Membaca arsip .god..."
            val blob = withContext(Dispatchers.IO) { FileUtil.readBytes(ctx, uri) }
            val entries = withContext(Dispatchers.Default) {
                GodFormat.unpack(blob, progress = { p, t -> setProgress(p, t) })
            }
            val lines = entries.joinToString("\n") { e ->
                if (e.isDir) "  📁 ${e.name}/" else "  📄 ${e.name}  (${e.data.size / 1024} KB)"
            }
            _status.value = "✔ ${entries.size} entri:\n$lines"
            entries.firstOrNull { !it.isDir }?.let { first ->
                _result.value = SaveResult(first.name, first.data)
            }
            _progress.value = 0f
        } catch (t: Throwable) {
            _status.value = "✘ Gagal: ${t.message ?: t.javaClass.simpleName}"
            _progress.value = 0f
        }
    }

    fun saveResult(ctx: Context, uri: Uri) = viewModelScope.launch {
        try {
            val r = _result.value ?: return@launch
            withContext(Dispatchers.IO) { FileUtil.writeBytes(ctx, uri, r.data) }
            _status.value = "✔ Tersimpan: ${r.suggestedName}"
            _result.value = null
        } catch (t: Throwable) {
            _status.value = "✘ Gagal simpan: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}