package zed.rainxch.core.domain.system

// What a stage means to a screen that is only drawing progress. The library and the details screen
// translate from here into their own state types, so "is this stage live" gets one answer instead
// of being re-derived in every consumer, where the tables can drift apart.
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
