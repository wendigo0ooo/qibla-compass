package com.wendigo.qibla

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the last uncaught exception to `/sdcard/Download/qibla_crash.txt` so it can be
 * inspected without adb. Installed from [MainActivity.onCreate].
 */
object CrashLog {

    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                write(app, thread, ex)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, ex)
        }
    }

    private fun write(context: Context, thread: Thread, ex: Throwable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        sw.appendLine("time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        sw.appendLine("thread: " + thread.name)
        sw.appendLine("device: " + Build.MANUFACTURER + " " + Build.MODEL + " sdk=" + Build.VERSION.SDK_INT)
        ex.printStackTrace(pw)
        pw.flush()

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "qibla_crash.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri, "w")?.use {
            it.write(sw.toString().toByteArray())
        }
    }
}
