package zed.rainxch.apps.presentation

internal data class VersionLineTarget(
    val targetVersion: String?,
    val isDowngrade: Boolean,
)

internal fun resolveVersionLine(
    installedVersion: String?,
    isPendingInstall: Boolean,
    pendingInstallVersion: String?,
    latestVersion: String?,
): VersionLineTarget {
    val target = if (isPendingInstall) pendingInstallVersion else latestVersion
    return VersionLineTarget(
        targetVersion = target?.takeIf { it.isNotBlank() },
        isDowngrade = isVersionDowngrade(installedVersion, target),
    )
}
