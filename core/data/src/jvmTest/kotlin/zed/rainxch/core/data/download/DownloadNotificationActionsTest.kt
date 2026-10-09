package zed.rainxch.core.data.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import zed.rainxch.core.domain.system.DownloadStage

class DownloadNotificationActionsTest {

    @Test
    fun a_live_download_offers_pause_and_delete() {
        assertEquals(
            setOf(DownloadNotificationAction.PAUSE, DownloadNotificationAction.DELETE),
            notificationActionsFor(DownloadStage.Queued),
        )
        assertEquals(
            setOf(DownloadNotificationAction.PAUSE, DownloadNotificationAction.DELETE),
            notificationActionsFor(DownloadStage.Downloading),
        )
    }

    @Test
    fun a_paused_download_offers_resume_and_delete() {
        assertEquals(
            setOf(DownloadNotificationAction.RESUME, DownloadNotificationAction.DELETE),
            notificationActionsFor(DownloadStage.Paused),
        )
    }

    @Test
    fun the_pause_button_is_never_offered_while_paused() {
        assertTrue(
            DownloadNotificationAction.PAUSE !in notificationActionsFor(DownloadStage.Paused),
            "pause after pause would be a no-op the user cannot escape",
        )
        assertTrue(
            DownloadNotificationAction.RESUME !in notificationActionsFor(DownloadStage.Downloading),
            "resume on a live transfer has nothing to restart",
        )
    }

    @Test
    fun stages_that_carry_no_notification_map_to_no_actions() {
        val silent =
            listOf(
                DownloadStage.Installing,
                DownloadStage.AwaitingInstall,
                DownloadStage.Completed,
                DownloadStage.Cancelled,
                DownloadStage.Failed,
            )

        for (stage in silent) {
            assertEquals(emptySet(), notificationActionsFor(stage), "stage $stage should be silent")
        }
    }
}
