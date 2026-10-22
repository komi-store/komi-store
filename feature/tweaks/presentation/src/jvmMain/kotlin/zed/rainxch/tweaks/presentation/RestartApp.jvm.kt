package zed.rainxch.tweaks.presentation

import co.touchlab.kermit.Logger
import kotlin.system.exitProcess

actual fun restartAppAfterLanguageChange() {
    try {
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
    } catch (t: Throwable) {
        Logger.w(t) {
            "restartAppAfterLanguageChange: relaunch failed, falling back to plain exit"
        }
    }
    exitProcess(0)
}
