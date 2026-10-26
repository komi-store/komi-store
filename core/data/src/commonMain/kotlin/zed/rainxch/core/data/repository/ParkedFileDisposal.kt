package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal

internal enum class ParkedFileDisposal {
    Gone,

    Survived,
}

internal fun tryDeleteParkedFile(path: String): ParkedFileDisposal {
    val file = java.io.File(path)
    val deleted = runCatching { file.delete() }.getOrDefault(false)
    if (deleted) return ParkedFileDisposal.Gone
    return if (file.exists()) ParkedFileDisposal.Survived else ParkedFileDisposal.Gone
}

internal suspend fun InstalledAppDao.discardParkedInstall(
    packageName: String,
    expectedPath: String?,
): ParkedInstallDisposal {
    if (expectedPath != null && tryDeleteParkedFile(expectedPath) == ParkedFileDisposal.Survived) {
        Logger.w { "Parked file for $packageName survived the discard; pointer kept" }
        return ParkedInstallDisposal.Retained
    }
    clearPendingInstallIfPathMatches(packageName = packageName, path = expectedPath)
    val remainingPath = getAppByPackage(packageName)?.pendingInstallFilePath
    return if (remainingPath != null) {
        Logger.d { "Parked install for $packageName still names $remainingPath; row kept" }
        ParkedInstallDisposal.Retained
    } else {
        Logger.d { "Discarded parked install for $packageName (path=${expectedPath ?: "none"})" }
        ParkedInstallDisposal.Discarded
    }
}
