package zed.rainxch.details.presentation

internal class DeferredSectionsGate(
    private val autoLoad: Boolean,
) {
    var isDeferred: Boolean = !autoLoad
        private set

    // The first request wins so the deferred README and release load runs once per screen.
    fun requestLoad(): Boolean {
        if (!isDeferred) return false
        isDeferred = false
        return true
    }
}
