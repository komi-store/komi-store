package zed.rainxch.core.domain.system

enum class DownloadStagePhase {
    Live,
    Installing,
    Resting,
    Failed,
    ;

    companion object {
        fun of(stage: DownloadStage): DownloadStagePhase =
            when (stage) {
                DownloadStage.Queued,
                DownloadStage.Downloading,
                -> Live

                DownloadStage.Installing -> Installing

                DownloadStage.Paused,
                DownloadStage.AwaitingInstall,
                DownloadStage.Completed,
                DownloadStage.Cancelled,
                -> Resting

                DownloadStage.Failed -> Failed
            }
    }
}

val DownloadStage.phase: DownloadStagePhase get() = DownloadStagePhase.of(this)
