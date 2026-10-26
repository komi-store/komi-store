package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.installation.InstalledApp

fun resolveExternalInstallVerdict(
    app: InstalledApp,
    newVersionName: String,
    newVersionCode: Long,
): VersionVerdict {
    val latestVersionCode = app.latestVersionCode ?: 0L
    return when {
        latestVersionCode > 0L && newVersionCode > 0L -> {
            if (newVersionCode >= latestVersionCode) {
                VersionVerdict.UP_TO_DATE
            } else {
                VersionVerdict.UPDATE_AVAILABLE
            }
        }

        newVersionName.isBlank() -> {
            VersionVerdict.UNKNOWN
        }

        else -> {
            listOfNotNull(app.latestVersionName, app.latestVersion)
                .asSequence()
                .filter { it.isNotBlank() }
                .map { compareAndDecide(newVersionName, it) }
                .firstOrNull { it != VersionVerdict.UNKNOWN }
                ?: VersionVerdict.UNKNOWN
        }
    }
}

private fun compareAndDecide(
    systemVersion: String,
    latestVersion: String,
): VersionVerdict {
    val system = VersionMath.normalizeVersion(systemVersion)
    val latest = VersionMath.normalizeVersion(latestVersion)
    return when {
        system.isEmpty() || latest.isEmpty() -> VersionVerdict.UNKNOWN
        !VersionMath.versionsReconcilable(system, latest) -> VersionVerdict.UNKNOWN
        VersionMath.compareVersions(system, latest) >= 0 -> VersionVerdict.UP_TO_DATE
        VersionMath.isVersionNewer(latest, system) -> VersionVerdict.UPDATE_AVAILABLE
        else -> VersionVerdict.UNKNOWN
    }
}
