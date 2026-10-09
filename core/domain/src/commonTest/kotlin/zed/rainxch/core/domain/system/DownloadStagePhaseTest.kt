package zed.rainxch.core.domain.system

import kotlin.test.Test
import kotlin.test.assertEquals

// The class of a stage is what the library and the details screen both act on, so it is pinned
// here rather than in either consumer: a stage moving between classes is a user-visible change in
// two screens at once, and this is the only place that can see it.
class DownloadStagePhaseTest {

    @Test
    fun everyStageMapsToExactlyOneClass() {
        // Exhaustive by construction: a new stage fails this test rather than silently falling
        // into whichever branch a consumer's `when` happens to end with.
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
        // The row is offered by the library's "can install" group, which renders from the database
        // rather than from progress, so a parked stage must not be treated as bytes still moving.
        assertEquals(DownloadStagePhase.Resting, DownloadStage.AwaitingInstall.phase)
    }

    @Test
    fun aFailedDownloadKeepsItsOwnClass() {
        // Resetting on a failure would hide it behind the same idle state as a finished download,
        // so the exception is not folded into Resting.
        assertEquals(DownloadStagePhase.Failed, DownloadStage.Failed.phase)
    }
}
