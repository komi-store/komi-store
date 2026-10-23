package zed.rainxch.core.domain.system

interface CrashLogSharer {
    suspend fun hasCrashLog(): Boolean

    fun shareLatest()
}
