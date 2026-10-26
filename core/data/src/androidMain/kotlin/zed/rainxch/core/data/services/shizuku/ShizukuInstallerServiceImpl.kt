package zed.rainxch.core.data.services.shizuku

import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ShizukuInstallerServiceImpl : IShizukuInstallerService.Stub() {
    companion object {
        private const val TAG = "ShizukuService"

        private const val STATUS_SUCCESS = 0
        private const val STATUS_FAILURE = -1
        private const val INSTALL_TIMEOUT_SECONDS = 120L
        private const val UNINSTALL_TIMEOUT_SECONDS = 30L
        private const val PIPE_BUFFER_SIZE = 64 * 1024

        private fun log(msg: String) = android.util.Log.d(TAG, msg)

        private fun logE(
            msg: String,
            e: Throwable? = null,
        ) = android.util.Log.e(TAG, msg, e)
    }

    override fun installPackage(
        pfd: ParcelFileDescriptor,
        fileSize: Long,
        installerPackageName: String?,
    ): Int {
        log("installPackage() called — fileSize=$fileSize, installer=$installerPackageName")
        log("Process UID: ${android.os.Process.myUid()}, PID: ${android.os.Process.myPid()}")

        return try {
            val safeInstaller = installerPackageName?.takeIf { it.isNotBlank() }
            val command =
                if (safeInstaller != null) {
                    arrayOf("pm", "install", "-i", safeInstaller, "-S", fileSize.toString())
                } else {
                    arrayOf("pm", "install", "-S", fileSize.toString())
                }
            log("Executing: ${command.joinToString(" ")}")

            val process = Runtime.getRuntime().exec(command)
            val writeThread = startPipe(pfd, process, fileSize)

            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()

            if (!pipeFinished(process, writeThread)) {
                STATUS_FAILURE
            } else {
                val exitCode = process.exitValue()
                log("pm install — exitCode=$exitCode, stdout='$stdout', stderr='$stderr'")

                if (exitCode == 0 && stdout.contains("Success")) {
                    log("Install SUCCESS")
                    STATUS_SUCCESS
                } else {
                    logE("Install FAILED — exitCode=$exitCode, stdout='$stdout', stderr='$stderr'")
                    STATUS_FAILURE
                }
            }
        } catch (e: IOException) {
            logE("installPackage() exception", e)
            STATUS_FAILURE
        } catch (e: InterruptedException) {
            logE("installPackage() exception", e)
            STATUS_FAILURE
        } catch (e: SecurityException) {
            logE("installPackage() exception", e)
            STATUS_FAILURE
        }
    }

    private fun startPipe(
        pfd: ParcelFileDescriptor,
        process: Process,
        fileSize: Long,
    ): Thread =
        Thread {
            try {
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    process.outputStream.use { output ->
                        val bytesWritten = input.copyTo(output, PIPE_BUFFER_SIZE)
                        output.flush()
                        log("APK piped to pm stdin: $bytesWritten bytes (expected: $fileSize)")
                    }
                }
            } catch (e: IOException) {
                logE("Error piping APK to pm stdin", e)
            }
        }.also { it.start() }

    private fun pipeFinished(
        process: Process,
        writeThread: Thread,
    ): Boolean {
        writeThread.join(TimeUnit.SECONDS.toMillis(INSTALL_TIMEOUT_SECONDS))
        if (writeThread.isAlive) {
            logE("Write thread timed out after ${INSTALL_TIMEOUT_SECONDS}s, aborting install")
            writeThread.interrupt()
            process.destroyForcibly()
            return false
        }

        val finished = process.waitFor(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            logE("pm install timed out after ${INSTALL_TIMEOUT_SECONDS}s, aborting")
            process.destroyForcibly()
        }
        return finished
    }

    override fun uninstallPackage(packageName: String): Int {
        log("uninstallPackage() called for: $packageName")
        return try {
            val command = arrayOf("pm", "uninstall", packageName)
            log("Executing: ${command.joinToString(" ")}")

            val process = Runtime.getRuntime().exec(command)
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()

            val finished = process.waitFor(UNINSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                logE("pm uninstall timed out after ${UNINSTALL_TIMEOUT_SECONDS}s, aborting")
                process.destroyForcibly()
                return STATUS_FAILURE
            }

            val exitCode = process.exitValue()
            log("pm uninstall — exitCode=$exitCode, stdout='$stdout', stderr='$stderr'")

            if (exitCode == 0 && stdout.contains("Success")) {
                log("Uninstall SUCCESS")
                STATUS_SUCCESS
            } else {
                logE("Uninstall FAILED — exitCode=$exitCode, stdout='$stdout', stderr='$stderr'")
                STATUS_FAILURE
            }
        } catch (e: IOException) {
            logE("uninstallPackage() exception", e)
            STATUS_FAILURE
        } catch (e: InterruptedException) {
            logE("uninstallPackage() exception", e)
            STATUS_FAILURE
        } catch (e: SecurityException) {
            logE("uninstallPackage() exception", e)
            STATUS_FAILURE
        }
    }

    override fun destroy() {
        log("destroy() — service being unbound")
    }
}
