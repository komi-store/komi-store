package zed.rainxch.core.data.services

import zed.rainxch.core.domain.system.CrashLogSharer

class DesktopCrashLogSharer : CrashLogSharer {
    override suspend fun hasCrashLog(): Boolean = false

    override fun shareLatest() = Unit
}
