package com.god.compressor.util

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream

object FileUtil {
    fun readBytes(ctx: Context, uri: Uri): ByteArray {
        ctx.contentResolver.openInputStream(uri).use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input!!.read(buf); if (r <= 0) break
                out.write(buf, 0, r)
            }
            return out.toByteArray()
        }
    }
    fun writeBytes(ctx: Context, uri: Uri, data: ByteArray) {
        ctx.contentResolver.openOutputStream(uri, "wt").use { it!!.write(data) }
    }
    fun displayName(ctx: Context, uri: Uri): String {
        var name = "file"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx) ?: name
            }
        }
        return name
    }
}
