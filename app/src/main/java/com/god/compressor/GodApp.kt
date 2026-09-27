package com.god.compressor

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pasang crash handler: tulis stack trace ke file + logcat
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val f = File(getExternalFilesDir(null), "crash_$ts.txt")
                f.writeText(throwable.stackTraceToString())
                Log.e("GOD", "Crash log: ${f.absolutePath}", throwable)
            } catch (t: Throwable) {
                Log.e("GOD", "Gagal tulis crash log", t)
            }
            prev?.uncaughtException(thread, throwable)
        }
    }
}