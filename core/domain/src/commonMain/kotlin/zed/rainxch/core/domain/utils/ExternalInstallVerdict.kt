package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.installation.InstalledApp

enum class VersionVerdict {
    UP_TO_DATE,

    UPDATE_AVAILABLE,

    UNKNOWN,
}

fun resolveExternalInstallVerdict(
    app: InstalledApp,
    newVersionName: String,
    newVersionCode: Long,
): VersionVerdict {
    val latestVersionCode = app.latestVersionCode ?: 0L
    if (latestVersionCode > 0L && newVersionCode > 0L) {
        return if (newVersionCode >= latestVersionCode) {
            VersionVerdict.UP_TO_DATE
        } else {
            VersionVerdict.UPDATE_AVAILABLE
        }
    }

    val latestName = app.latestVersionName
    if (!latestName.isNullOrBlank() && newVersionName.isNotBlank()) {
        val verdict = compareAndDecide(newVersionName, latestName)
        if (verdict != VersionVerdict.UNKNOWN) return verdict
    }

    val latestTag = app.latestVersion
    if (!latestTag.isNullOrBlank() && newVersionName.isNotBlank()) {
        val verdict = compareAndDecide(newVersionName, latestTag)
        if (verdict != VersionVerdict.UNKNOWN) return verdict
    }

    return VersionVerdict.UNKNOWN
}

private fun compareAndDecide(
    systemVersion: String,
    latestVersion: String,
): VersionVerdict {
    val system = VersionMath.normalizeVersion(systemVersion)
    val latest = VersionMath.normalizeVersion(latestVersion)
    if (system.isEmpty() || latest.isEmpty()) return VersionVerdict.UNKNOWN

    if (!VersionMath.versionsReconcilable(system, latest)) return VersionVerdict.UNKNOWN

    val cmp = VersionMath.compareVersions(system, latest)
    return when {
        cmp >= 0 -> VersionVerdict.UP_TO_DATE
        VersionMath.isVersionNewer(latest, system) -> VersionVerdict.UPDATE_AVAILABLE
        else -> VersionVerdict.UNKNOWN
    }
}
