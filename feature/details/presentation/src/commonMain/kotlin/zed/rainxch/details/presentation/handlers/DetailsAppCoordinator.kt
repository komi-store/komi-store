package zed.rainxch.details.presentation.handlers

// What the page does: installs and downloads, hand-offs to other installers, the installed app and the repository.
class DetailsAppCoordinator internal constructor(
    internal val installs: DetailsInstallHandler,
    internal val handoffs: DetailsHandoffHandler,
    internal val app: DetailsAppHandler,
    internal val repo: DetailsRepoHandler,
    private val downloads: OrchestratedDownloads,
) {
    internal fun observe(session: DetailsSession) = downloads.observeForDisplay(session)

    internal fun start(session: DetailsSession) {
        app.observeApkInspectCoachmark(session)
        repo.observeCurrentUserForBadge(session)
    }

    internal fun onCleared(session: DetailsSession) = downloads.onCleared(session)
}
