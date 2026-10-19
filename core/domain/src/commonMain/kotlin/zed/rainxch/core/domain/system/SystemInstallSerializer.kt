package zed.rainxch.core.domain.system

interface SystemInstallSerializer {
    suspend fun awaitFreeAndMarkPending(
        packageName: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    )

    fun markCompleted(packageName: String)

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 15_000L
    }
}
