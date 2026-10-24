package zed.rainxch.githubstore.app.diagnostics

import android.content.Context
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.services.CrashLogs
import zed.rainxch.core.domain.diagnostics.DiagnosticLogRedactor
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// Every run is logged: the log never leaves the device unless shared, and the run that crashed is
// the one worth sharing. Each start appends a session header; the writer trims the file at 2 MB.
object DiagnosticSession {
    private val lock = Any()
    private val timestampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    @Volatile
    private var appContext: Context? = null

    private var writer: KermitDiagnosticLogWriter? = null

    fun install(context: Context) {
        val applicationContext = context.applicationContext
        synchronized(lock) {
            appContext = applicationContext
            if (writer != null) return
            val diagnosticsDir = diagnosticsDir(applicationContext).apply { mkdirs() }
            writer =
                KermitDiagnosticLogWriter(logFile = File(diagnosticsDir, SESSION_LOG_NAME)).also {
                    it.startSession(sessionHeader())
                    Logger.addLogWriter(it)
                }
        }
    }

    suspend fun buildShareBundle(appVersion: String): String? {
        val context = appContext ?: return null
        return withContext(Dispatchers.IO) {
            try {
                buildBundle(context, appVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                null
            }
        }
    }

    private fun buildBundle(
        context: Context,
        appVersion: String,
    ): String {
        val logFile = sessionLogFile(context)
        val bundle =
            buildString {
                appendLine("Komi Store diagnostic bundle")
                appendLine("generated: ${OffsetDateTime.now().format(timestampFormat)}")
                appendLine("app: Komi Store $appVersion")
                appendLine("package: ${context.packageName}")
                appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("os: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                if (hasSessionLog(context)) {
                    appendLine()
                    appendLine("=== app.log ===")
                    appendLine(logFile.readText())
                }
                CrashLogs.latest(context)?.let { crashReport ->
                    appendLine()
                    appendLine("=== ${crashReport.name} ===")
                    appendLine(crashReport.readText())
                }
            }
        return DiagnosticLogRedactor.redact(bundle)
    }

    private fun sessionHeader(): String = "=== diagnostic session ${OffsetDateTime.now().format(timestampFormat)} ==="

    private fun hasSessionLog(context: Context): Boolean = sessionLogFile(context).let { it.isFile && it.length() > 0 }

    private fun sessionLogFile(context: Context): File = File(diagnosticsDir(context), SESSION_LOG_NAME)

    private fun diagnosticsDir(context: Context): File = File(context.filesDir, DIAGNOSTICS_DIR)

    private const val DIAGNOSTICS_DIR = "diagnostics"
    private const val SESSION_LOG_NAME = "app.log"
}
