package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.*
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val DATE_ONLY_LENGTH = 10
private const val YEAR_DASH_INDEX = 4
private const val MONTH_DASH_INDEX = 7
private const val TIME_START_INDEX = 11
private const val SHORT_OFFSET_LENGTH = 3
private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7

@OptIn(ExperimentalTime::class)
private fun parseIsoInstantLenient(isoInstant: String): Instant? {
    val trimmed = isoInstant.trim()
    if (trimmed.isEmpty()) return null
    return lenientIsoCandidates(trimmed).firstNotNullOfOrNull { candidate ->
        runCatching { Instant.parse(candidate) }.getOrNull()
    }
}

private fun lenientIsoCandidates(trimmed: String): Sequence<String> =
    sequence {
        yield(trimmed)
        if (isDateOnly(trimmed)) yield(trimmed + "T00:00:00Z")
        yield(withShortOffsetMinutes(withTimeSeparator(trimmed)))
        withMissingTimeFields(trimmed)?.let { yield(it) }
    }

private fun isDateOnly(s: String): Boolean = s.length == DATE_ONLY_LENGTH && s[YEAR_DASH_INDEX] == '-' && s[MONTH_DASH_INDEX] == '-'

private fun withTimeSeparator(s: String): String =
    if (s.length > DATE_ONLY_LENGTH && s[DATE_ONLY_LENGTH] == ' ') {
        s.substring(0, DATE_ONLY_LENGTH) + "T" + s.substring(DATE_ONLY_LENGTH + 1)
    } else {
        s
    }

private fun withShortOffsetMinutes(s: String): String = if (s.contains('T') && endsWithShortOffset(s)) "$s:00" else s

private fun endsWithShortOffset(s: String): Boolean {
    if (s.length < SHORT_OFFSET_LENGTH) return false
    val sign = s[s.length - SHORT_OFFSET_LENGTH]
    return (sign == '+' || sign == '-') && s.takeLast(2).all { it.isDigit() }
}

private fun withMissingTimeFields(s: String): String? {
    val tzStart = s.indexOfAny(charArrayOf('Z', '+', '-'), startIndex = TIME_START_INDEX)
    if (tzStart < 0) return null
    val head = s.substring(0, tzStart)
    val tail = s.substring(tzStart)
    return when (head.count { it == ':' }) {
        1 -> "$head:00$tail"
        0 -> "$head:00:00$tail"
        else -> null
    }
}

@OptIn(ExperimentalTime::class)
fun daysSinceIso(isoInstant: String?): Int? {
    val instant = isoInstant?.let { parseIsoInstantLenient(it) } ?: return null
    return (Clock.System.now() - instant).inWholeDays.toInt()
}

@OptIn(ExperimentalTime::class)
@Composable
fun formatReleasedAgo(isoInstant: String?): String? {
    val instant = isoInstant?.let { parseIsoInstantLenient(it) } ?: return null
    val diff = Clock.System.now() - instant
    val minutes = if (diff.isNegative()) 0L else diff.inWholeMinutes
    return when {
        minutes < 1L -> stringResource(Res.string.just_now)
        minutes < 60L -> stringResource(Res.string.time_minutes_ago, minutes.toInt())
        diff.inWholeHours < 24L -> stringResource(Res.string.time_hours_ago, diff.inWholeHours.toInt())
        diff.inWholeDays < 7L -> stringResource(Res.string.time_days_ago, diff.inWholeDays.toInt())
        diff.inWholeDays < 30L -> stringResource(Res.string.time_weeks_ago, (diff.inWholeDays / 7.0).roundToInt())
        diff.inWholeDays < 365L -> stringResource(Res.string.time_months_ago, (diff.inWholeDays / 30.0).roundToInt())
        else -> stringResource(Res.string.time_years_ago, (diff.inWholeDays / 365.0).roundToInt())
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun formatRelativeLong(isoInstant: String): String {
    val instant = parseIsoInstantLenient(isoInstant) ?: return isoInstant
    val duration = Clock.System.now() - instant
    return when {
        duration.inWholeDays > 365 -> {
            stringResource(Res.string.time_years_ago, (duration.inWholeDays / 365).toInt())
        }

        duration.inWholeDays > 30 -> {
            stringResource(Res.string.time_months_ago, (duration.inWholeDays / 30).toInt())
        }

        duration.inWholeDays > 0 -> {
            stringResource(Res.string.time_days_ago, duration.inWholeDays.toInt())
        }

        duration.inWholeHours > 0 -> {
            stringResource(Res.string.time_hours_ago, duration.inWholeHours.toInt())
        }

        duration.inWholeMinutes > 0 -> {
            stringResource(Res.string.time_minutes_ago, duration.inWholeMinutes.toInt())
        }

        else -> {
            stringResource(Res.string.just_now)
        }
    }
}

@OptIn(ExperimentalTime::class)
fun formatIsoDate(isoTimestamp: String?): String? {
    val instant = isoTimestamp?.let { parseIsoInstantLenient(it) } ?: return null
    return instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}

@OptIn(ExperimentalTime::class)
fun formatIsoDateOrRaw(iso: String): String =
    if (iso.length > DATE_ONLY_LENGTH) formatIsoDate(iso) ?: iso.take(DATE_ONLY_LENGTH) else iso.take(DATE_ONLY_LENGTH)

@OptIn(ExperimentalTime::class)
fun formatEpochDate(timestamp: Long): String? {
    if (timestamp <= 0L) return null
    return Instant
        .fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
}

@OptIn(ExperimentalTime::class)
@Composable
fun formatLastChecked(timestamp: Long): String {
    val diffMs = Clock.System.now().toEpochMilliseconds() - timestamp
    val minutes = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    return when {
        minutes < 1 -> stringResource(Res.string.last_checked_just_now)
        minutes < 60 -> stringResource(Res.string.last_checked_minutes_ago, minutes.toInt())
        else -> stringResource(Res.string.last_checked_hours_ago, hours.toInt())
    }
}

@OptIn(ExperimentalTime::class)
suspend fun formatAddedAt(epochMillis: Long): String {
    val updated = Instant.fromEpochMilliseconds(epochMillis)
    val now = Clock.System.now()
    val diff: Duration = now - updated

    val hoursDiff = diff.inWholeHours
    val daysDiff = diff.inWholeDays

    return when {
        hoursDiff < 1 -> {
            getString(Res.string.added_just_now)
        }

        hoursDiff < HOURS_PER_DAY -> {
            getString(Res.string.added_hours_ago, hoursDiff)
        }

        daysDiff == 1L -> {
            getString(Res.string.added_yesterday)
        }

        daysDiff < DAYS_PER_WEEK -> {
            getString(Res.string.added_days_ago, daysDiff)
        }

        else -> {
            val date =
                updated
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            getString(Res.string.added_on_date, date.toString())
        }
    }
}
