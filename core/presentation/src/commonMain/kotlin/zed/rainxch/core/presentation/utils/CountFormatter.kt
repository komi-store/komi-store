package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun formatCount(count: Int): String =
    when {
        count >= 1_000_000 -> stringResource(Res.string.count_millions, count / 1_000_000)
        count >= 1000 -> stringResource(Res.string.count_thousands, count / 1000)
        else -> count.toString()
    }

@Composable
fun formatCount(count: Long): String =
    when {
        count >= 1_000_000L -> stringResource(Res.string.count_millions, (count / 1_000_000L).toInt())
        count >= 1000L -> stringResource(Res.string.count_thousands, (count / 1000L).toInt())
        else -> count.toString()
    }

private const val THOUSAND = 1_000L
private const val MILLION = 1_000_000L
private const val TENTHS = 10

fun formatCompactCount(count: Long): String =
    when {
        count >= MILLION -> "${inTenths(count, MILLION)}M"
        count >= THOUSAND -> "${inTenths(count, THOUSAND)}k"
        else -> count.toString()
    }

fun formatCompactCount(count: Int): String = formatCompactCount(count.toLong())

// Truncates to one decimal, as 1_250 becomes 1.2k.
private fun inTenths(
    count: Long,
    unit: Long,
): String = trimTrailingZero(count / (unit / TENTHS) / TENTHS.toDouble())

private fun trimTrailingZero(value: Double): String {
    val text = value.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
