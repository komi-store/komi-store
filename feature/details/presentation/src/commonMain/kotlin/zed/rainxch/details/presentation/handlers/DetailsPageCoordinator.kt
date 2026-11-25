package zed.rainxch.details.presentation.handlers

import zed.rainxch.details.presentation.DetailsAction

// What the page shows: loading and refreshing it, picking releases and assets, and its README and release notes.
class DetailsPageCoordinator internal constructor(
    private val loader: DetailsPageLoader,
    private val refresher: DetailsRefresher,
    internal val picker: DetailsReleasePicker,
    internal val content: DetailsContentHandler,
    private val extras: DetailsRepoExtras,
) {
    internal fun observe(session: DetailsSession) {
        content.observeAutoTranslateSetting(session)
        extras.observeReviewChanges(session)
    }

    internal fun start(session: DetailsSession) {
        loader.loadInitial(session)
        picker.observeChannelChipCoachmark(session)
        picker.observeShowAllPlatforms(session)
    }

    internal fun onLoadAction(
        session: DetailsSession,
        action: DetailsAction.Load,
    ) {
        when (action) {
            DetailsAction.Retry -> loader.retry(session)
            DetailsAction.RetryReleases -> refresher.retryReleases(session)
            DetailsAction.Refresh -> refresher.refresh(session)
        }
    }
}
