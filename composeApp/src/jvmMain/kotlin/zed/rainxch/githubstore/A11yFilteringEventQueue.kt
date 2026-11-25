package zed.rainxch.githubstore

import java.awt.AWTEvent
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicBoolean

// EventQueue.dispatchEvent runs any listener's code, so any RuntimeException can surface; only Compose's a11y crash is dropped.
internal class A11yFilteringEventQueue : EventQueue() {
    private val warned = AtomicBoolean(false)

    override fun dispatchEvent(event: AWTEvent) {
        try {
            super.dispatchEvent(event)
        } catch (ex: RuntimeException) {
            if (!A11yCrashGuard.isComposeA11yCrash(ex)) throw ex
            if (warned.compareAndSet(false, true)) {
                System.err.println(
                    "[A11yCrashGuard] Suppressed Compose a11y crash on macOS " +
                        "(known issue, see Komi-Store#330 / #639 / #640 / #684). Further occurrences silenced.",
                )
            }
        }
    }
}
