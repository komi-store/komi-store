package zed.rainxch.core.data.services

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Reports stay in app storage and leave the device only when the user shares one.
object CrashLogs {
    private const val DIR = "crashes"
    private const val MAX_FILES = 3

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun latest(context: Context): File? = crashDir(context).listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }

    private fun write(
        context: Context,
        thread: Thread,
        error: Throwable,
    ) {
        val dir = crashDir(context).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val report =
            buildString {
                appendLine("Komi Store crash report")
                appendLine("time: ${Date()}")
                appendLine("thread: ${thread.name}")
                appendLine("package: ${context.packageName}")
                appendLine("android: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
                appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine()
                appendLine(error.stackTraceToString())
            }
        File(dir, "crash-$stamp.txt").writeText(report)
        dir
            .listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }

    private fun crashDir(context: Context) = File(context.filesDir, DIR)
}
