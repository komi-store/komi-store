package zed.rainxch.apps.presentation

// The "to" side of a row's version line, and whether that move is backwards. Pure so the three
// paths a version line can take are pinned by a test without a string resource or a ViewModel.
internal data class VersionLineTarget(
    val targetVersion: String?,
    val isDowngrade: Boolean,
)

// A pending install shows the version its parked file carries — which may be older than the one
// installed, a rollback the user asked for — so that the direction is visible. A normal update
// shows the store's latest; a row with neither shows only its installed version.
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
