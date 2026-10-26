package zed.rainxch.core.presentation.utils

private const val BYTES_PER_UNIT = 1_024L

fun formatFileSize(
    bytes: Long,
    decimals: Int = 1,
): String {
    if (bytes < BYTES_PER_UNIT) return "$bytes B"

    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var size = bytes.toDouble() / BYTES_PER_UNIT
    var index = 0
    while (size >= BYTES_PER_UNIT && index < units.lastIndex) {
        size /= BYTES_PER_UNIT
        index++
    }

    return "%.${decimals}f ${units[index]}".format(size)
}
