package zed.rainxch.details.presentation.handlers

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import zed.rainxch.details.presentation.model.InstallLogItem
import zed.rainxch.details.presentation.model.LogResult
import kotlin.time.Clock

private val LogTimeFormat =
    LocalDateTime.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        day()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

internal fun DetailsSession.appendLog(
    assetName: String,
    size: Long,
    tag: String,
    result: LogResult,
) {
    val now =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .format(LogTimeFormat)
    val item =
        InstallLogItem(
            timeIso = now,
            assetName = assetName,
            assetSizeBytes = size,
            releaseTag = tag,
            result = result,
        )
    update { it.copy(installLogs = listOf(item) + it.installLogs) }
}
