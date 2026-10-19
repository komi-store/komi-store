package zed.rainxch.core.domain.system

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadStagePhaseTest {
    @Test
    fun everyStageMapsToExactlyOneClass() {
        val byClass =
            DownloadStage.entries.groupBy { it.phase }

        assertEquals(
            listOf(DownloadStage.Queued, DownloadStage.Downloading),
            byClass[DownloadStagePhase.Live],
        )
        assertEquals(listOf(DownloadStage.Installing), byClass[DownloadStagePhase.Installing])
        assertEquals(
            listOf(
                DownloadStage.Paused,
                DownloadStage.AwaitingInstall,
                DownloadStage.Completed,
                DownloadStage.Cancelled,
            ),
            byClass[DownloadStagePhase.Resting],
        )
        assertEquals(listOf(DownloadStage.Failed), byClass[DownloadStagePhase.Failed])

        val classified = byClass.values.sumOf { it.size }
        assertEquals(
            DownloadStage.entries.size,
            classified,
            "every stage must appear in exactly one class",
        )
    }

    @Test
    fun aParkedDownloadIsRestingRatherThanLive() {
        assertEquals(DownloadStagePhase.Resting, DownloadStage.AwaitingInstall.phase)
    }

    @Test
    fun aFailedDownloadKeepsItsOwnClass() {
        assertEquals(DownloadStagePhase.Failed, DownloadStage.Failed.phase)
    }
}
