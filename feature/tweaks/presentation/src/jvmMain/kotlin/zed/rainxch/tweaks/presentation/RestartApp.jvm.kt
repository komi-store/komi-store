package zed.rainxch.tweaks.presentation

import co.touchlab.kermit.Logger
import java.io.IOException
import kotlin.system.exitProcess

actual fun restartAppAfterLanguageChange() {
    try {
        relaunch()
    } catch (e: IOException) {
        relaunchFailed(e)
    } catch (e: SecurityException) {
        relaunchFailed(e)
    } catch (e: UnsupportedOperationException) {
        relaunchFailed(e)
    }
    exitProcess(0)
}

private fun relaunch() {
    val info = ProcessHandle.current().info()
    val command = info.command().orElse(null)
    if (command != null) {
        val arguments = info.arguments().orElse(emptyArray())
        ProcessBuilder(listOf(command) + arguments.toList())
            .inheritIO()
            .start()
    } else {
        Logger.w {
            "restartAppAfterLanguageChange: ProcessHandle has no command; exiting without relaunch"
        }
    }
}

private fun relaunchFailed(e: Exception) {
    Logger.w(e) {
        "restartAppAfterLanguageChange: relaunch failed, falling back to plain exit"
    }
}
