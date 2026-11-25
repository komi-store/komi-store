package zed.rainxch.githubstore

import zed.rainxch.core.domain.system.DesktopOs
import java.awt.Toolkit
import java.util.concurrent.atomic.AtomicBoolean

object A11yCrashGuard {
    private const val COMPOSE_ACCESSIBILITY_ENABLE = "compose.accessibility.enable"

    private val warnedUncaught = AtomicBoolean(false)

    fun install() {
        if (!DesktopOs.isMac) return

        disableComposeAccessibilityBridgeByDefault()

        Toolkit.getDefaultToolkit().systemEventQueue.push(A11yFilteringEventQueue())

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isComposeA11yCrash(throwable)) {
                if (warnedUncaught.compareAndSet(false, true)) {
                    System.err.println(
                        "[A11yCrashGuard] Suppressed Compose a11y crash via uncaught-exception path " +
                            "(known issue, see Komi-Store#330 / #639 / #640 / #684). Further occurrences silenced.",
                    )
                }
                return@setDefaultUncaughtExceptionHandler
            }

            previous?.uncaughtException(thread, throwable)
                ?: throwable.printStackTrace(System.err)
        }
    }

    private fun disableComposeAccessibilityBridgeByDefault() {
        if (System.getProperty(COMPOSE_ACCESSIBILITY_ENABLE) != null) return

        // Compose's AWT accessibility bridge can still crash on macOS on detached components, so it stays off unless opted in.
        System.setProperty(COMPOSE_ACCESSIBILITY_ENABLE, "false")
        System.err.println(
            "[A11yCrashGuard] Disabled Compose accessibility bridge on macOS " +
                "(known issue, see Komi-Store#330 / #639 / #640).",
        )
    }

    internal fun isComposeA11yCrash(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current.stackTrace.any { frame ->
                    frame.className.startsWith("androidx.compose.ui.platform.a11y") ||
                        frame.className.startsWith("sun.lwawt.macosx.CAccessib")
                }
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
