package zed.rainxch.apps.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.OrchestratedDownload

// The two decisions the library's download visibility rests on. The ViewModel around them is
// plumbing; these are the parts that were worth getting wrong quietly.
class AppsDownloadMirrorTest {

    private val cardStages =
        setOf(
            DownloadStage.Queued,
            DownloadStage.Downloading,
            DownloadStage.Installing,
            DownloadStage.AwaitingInstall,
            DownloadStage.Failed,
        )

    private fun entry(key: String, stage: DownloadStage, error: String? = null) =
        OrchestratedDownload(
            id = key,
            packageName = key,
            repoOwner = "owner",
            repoName = "name",
            displayAppName = "Name",
            assetName = "name.apk",
            assetSize = 10L,
            downloadUrl = "https://example.invalid/name.apk",
            releaseTag = "v1",
            filePath = null,
            installPolicy = InstallPolicy.DeferUntilUserAction,
            stage = stage,
            progressPercent = null,
            errorMessage = error,
        )

    @Test
    fun aDownloadTheLibraryHasNoRowForBecomesATransientCard() {
        val snapshot = mapOf("owner/name" to entry("owner/name", DownloadStage.Downloading))

        val transient = transientDownloads(snapshot, knownPackages = setOf("com.other"), cardStages)

        assertEquals(listOf("owner/name"), transient.map { it.packageName })
    }

    @Test
    fun aDownloadTheLibraryCanShowItselfIsNotAlsoShownAsATransientCard() {
        // Both surfaces would otherwise render the same transfer, one of them without a database row.
        val snapshot = mapOf("com.app" to entry("com.app", DownloadStage.Downloading))

        assertTrue(transientDownloads(snapshot, knownPackages = setOf("com.app"), cardStages).isEmpty())
    }

    @Test
    fun aSettledDownloadIsNotACard() {
        // Completed and Cancelled are absent from the card stages: the row is either in a library
        // group by then or gone entirely, and a card would outlive the thing it describes.
        val snapshot =
            mapOf(
                "owner/name" to entry("owner/name", DownloadStage.Completed),
                "owner/second" to entry("owner/second", DownloadStage.Cancelled),
            )

        assertTrue(transientDownloads(snapshot, emptySet(), cardStages).isEmpty())
    }

    @Test
    fun theDefaultCardStagesAreTheProductionSetItself() {
        // The default parameter is the production decision: dropping a stage from the real set
        // (Failed) must fail here, not pass against a copy of the set declared in a test.
        val failed = mapOf("owner/name" to entry("owner/name", DownloadStage.Failed))
        assertEquals(1, transientDownloads(failed, knownPackages = emptySet()).size)

        val terminal =
            mapOf(
                "owner/name" to entry("owner/name", DownloadStage.Completed),
                "owner/second" to entry("owner/second", DownloadStage.Cancelled),
            )
        assertTrue(transientDownloads(terminal, knownPackages = emptySet()).isEmpty())
    }

    @Test
    fun aFailureKeepsItsOwnMessageAndFallsBackWhenItHasNone() {
        assertEquals(
            UpdateState.Error("disk full"),
            mirroredUpdateState(DownloadStage.Failed, errorMessage = "disk full", failedFallback = "fallback"),
        )
        assertEquals(
            UpdateState.Error("fallback"),
            mirroredUpdateState(DownloadStage.Failed, errorMessage = null, failedFallback = "fallback"),
        )
    }

    @Test
    fun aParkedDownloadLeavesTheRowIdleSoTheLibraryGroupCanOfferIt() {
        // "Ready to install" renders from the database. A row still marked Downloading would draw a
        // progress bar where the install button belongs.
        assertEquals(
            UpdateState.Idle,
            mirroredUpdateState(DownloadStage.AwaitingInstall, errorMessage = null, failedFallback = "f"),
        )
        assertEquals(
            UpdateState.Idle,
            mirroredUpdateState(DownloadStage.Completed, errorMessage = null, failedFallback = "f"),
        )
    }

    @Test
    fun liveAndInstallingStagesKeepTheirOwnRowStates() {
        assertEquals(
            UpdateState.Downloading,
            mirroredUpdateState(DownloadStage.Queued, errorMessage = null, failedFallback = "f"),
        )
        assertEquals(
            UpdateState.Downloading,
            mirroredUpdateState(DownloadStage.Downloading, errorMessage = null, failedFallback = "f"),
        )
        assertEquals(
            UpdateState.Installing,
            mirroredUpdateState(DownloadStage.Installing, errorMessage = null, failedFallback = "f"),
        )
    }
}
