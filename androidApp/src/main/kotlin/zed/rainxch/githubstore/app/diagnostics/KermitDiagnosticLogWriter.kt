package zed.rainxch.githubstore.app.diagnostics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import zed.rainxch.core.domain.diagnostics.DiagnosticLogRedactor
import zed.rainxch.core.domain.diagnostics.DiagnosticLogRing
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

internal class KermitDiagnosticLogWriter(
    private val logFile: File,
) : LogWriter() {
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, THREAD_NAME).apply { isDaemon = true }
        }

    private var stream: FileOutputStream? = null
    private var bytesWritten = 0L

    override fun isLoggable(
        tag: String,
        severity: Severity,
    ): Boolean = true

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        val record =
            LogRecord(
                timestamp = OffsetDateTime.now().format(TIMESTAMP_FORMAT),
                severity = severity,
                tag = tag,
                threadName = Thread.currentThread().name,
                message = message,
                throwable = throwable,
            )
        executor.execute { append(record) }
    }

    // Appends, so the previous run's log (often the one that crashed) is still there to share.
    fun startSession(header: String) {
        executor.execute {
            closeStream()
            runCatching {
                logFile.parentFile?.mkdirs()
                logFile.appendText("$header\n")
                bytesWritten = logFile.length()
                stream = FileOutputStream(logFile, true)
            }.onFailure { closeStream() }
            if (bytesWritten > MAX_BYTES) trim()
        }
    }

    private fun append(record: LogRecord) {
        val output = ensureStream()
        if (output != null) {
            val content = DiagnosticLogRedactor.redact(buildRecord(record))
            val bytes = content.encodeToByteArray()
            try {
                output.write(bytes)
                output.flush()
                bytesWritten += bytes.size
                if (bytesWritten > MAX_BYTES) trim()
            } catch (_: IOException) {
                closeStream()
            }
        }
    }

    private fun buildRecord(record: LogRecord): String =
        buildString {
            append(record.timestamp)
            append(' ')
            append(record.severity.name)
            append('/')
            append(record.tag.ifBlank { DEFAULT_TAG })
            append(" [")
            append(record.threadName)
            append("] ")
            append(record.message)
            append('\n')
            record.throwable?.let { throwable ->
                append(throwable.stackTraceToString())
                if (!endsWith('\n')) append('\n')
            }
        }

    private fun ensureStream(): FileOutputStream? {
        stream?.let { return it }
        return runCatching {
            logFile.parentFile?.mkdirs()
            FileOutputStream(logFile, true).also {
                bytesWritten = logFile.length()
                stream = it
            }
        }.getOrNull()
    }

    private fun trim() {
        closeStream()
        runCatching {
            val content = logFile.readText()
            val trimmed =
                DiagnosticLogRing.trimOldest(
                    content = content,
                    maxBytes = MAX_BYTES.toInt(),
                    targetBytes = TRIM_TARGET_BYTES,
                )
            logFile.writeText(trimmed)
            bytesWritten = logFile.length()
        }
    }

    private fun closeStream() {
        runCatching { stream?.close() }
        stream = null
    }

    private data class LogRecord(
        val timestamp: String,
        val severity: Severity,
        val tag: String,
        val threadName: String,
        val message: String,
        val throwable: Throwable?,
    )

    private companion object {
        const val THREAD_NAME = "diagnostic-log-writer"
        const val DEFAULT_TAG = "app"
        const val MAX_BYTES = 2L * 1024L * 1024L
        const val TRIM_TARGET_BYTES = 1024 * 1024

        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    }
}
