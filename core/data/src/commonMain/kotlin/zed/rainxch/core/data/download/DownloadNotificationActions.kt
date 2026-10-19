package zed.rainxch.core.data.download

import zed.rainxch.core.domain.system.DownloadStage

enum class DownloadNotificationAction {
    PAUSE,
    RESUME,
    DELETE,
}

fun notificationActionsFor(stage: DownloadStage): Set<DownloadNotificationAction> =
    when (stage) {
        DownloadStage.Queued,
        DownloadStage.Downloading,
        -> {
            setOf(DownloadNotificationAction.PAUSE, DownloadNotificationAction.DELETE)
        }

        DownloadStage.Paused -> {
            setOf(DownloadNotificationAction.RESUME, DownloadNotificationAction.DELETE)
        }

        DownloadStage.Installing,
        DownloadStage.AwaitingInstall,
        DownloadStage.Completed,
        DownloadStage.Cancelled,
        DownloadStage.Failed,
        -> {
            emptySet()
        }
    }
