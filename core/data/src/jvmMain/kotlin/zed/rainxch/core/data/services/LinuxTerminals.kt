package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.model.LinuxTerminal
import java.io.IOException

internal class LinuxTerminals(
    private val host: DesktopHost,
) {
    fun available(): List<LinuxTerminal> =
        DETECTION_ORDER
            .filter { (_, command) ->
                host.hasCommand(command).also { found ->
                    if (found) Logger.d { "Found terminal: $command" }
                }
            }.map { (terminal, _) -> terminal }

    fun run(
        command: String,
        terminals: List<LinuxTerminal>,
    ) {
        for (terminal in terminals) {
            try {
                Logger.d { "Trying terminal: ${terminal.name}" }
                ProcessBuilder(launchArgs(terminal, command)).start()
                Logger.d { "Terminal opened successfully: ${terminal.name}" }
                return
            } catch (e: IOException) {
                Logger.w { "Failed to open ${terminal.name}: ${e.message}" }
            }
        }
        throw IOException("Could not open any terminal emulator")
    }

    private fun launchArgs(
        terminal: LinuxTerminal,
        command: String,
    ): List<String> =
        when (terminal) {
            LinuxTerminal.GNOME_TERMINAL -> listOf("gnome-terminal", "--", "bash", "-c", command)
            LinuxTerminal.KONSOLE -> listOf("konsole", "-e", "bash", "-c", command)
            LinuxTerminal.XTERM -> listOf("xterm", "-e", "bash", "-c", command)
            LinuxTerminal.XFCE4_TERMINAL -> listOf("xfce4-terminal", "-e", "bash -c \"$command\"")
            LinuxTerminal.ALACRITTY -> listOf("alacritty", "-e", "bash", "-c", command)
            LinuxTerminal.KITTY -> listOf("kitty", "bash", "-c", command)
            LinuxTerminal.TILIX -> listOf("tilix", "-e", "bash -c \"$command\"")
            LinuxTerminal.MATE_TERMINAL -> listOf("mate-terminal", "-e", "bash -c \"$command\"")
        }

    private companion object {
        val DETECTION_ORDER =
            listOf(
                LinuxTerminal.GNOME_TERMINAL to "gnome-terminal",
                LinuxTerminal.KONSOLE to "konsole",
                LinuxTerminal.XFCE4_TERMINAL to "xfce4-terminal",
                LinuxTerminal.ALACRITTY to "alacritty",
                LinuxTerminal.KITTY to "kitty",
                LinuxTerminal.TILIX to "tilix",
                LinuxTerminal.MATE_TERMINAL to "mate-terminal",
                LinuxTerminal.XTERM to "xterm",
            )
    }
}
