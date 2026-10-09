package zed.rainxch.core.domain.system

enum class DownloadStage {

    Queued,

    Downloading,

    Paused,

    Installing,

    AwaitingInstall,

    Completed,

    Cancelled,

    Failed,
}
