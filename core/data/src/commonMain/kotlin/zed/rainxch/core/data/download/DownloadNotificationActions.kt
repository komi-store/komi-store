package zed.rainxch.core.data.download

import zed.rainxch.core.domain.system.DownloadStage

// The buttons a download notification carries, per stage. A live transfer offers Pause, a parked
// one offers Resume, and both offer Delete; every other stage carries no notification at all, so it
// maps to an empty set and the observer clears whatever was there. Kept pure and Android-free so the
// mapping can be tested on the JVM without a notification manager.
enum class DownloadNotificationAction {
    PAUSE,
    RESUME,
    DELETE,
}

fun notificationActionsFor(stage: DownloadStage): Set<DownloadNotificationAction> =
    when (stage) {
        DownloadStage.Queued,
        DownloadStage.Downloading,
        -> setOf(DownloadNotificationAction.PAUSE, DownloadNotificationAction.DELETE)

        DownloadStage.Paused ->
            setOf(DownloadNotificationAction.RESUME, DownloadNotificationAction.DELETE)

        DownloadStage.Installing,
        DownloadStage.AwaitingInstall,
        DownloadStage.Completed,
        DownloadStage.Cancelled,
        DownloadStage.Failed,
        -> emptySet()
    }
