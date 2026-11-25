package zed.rainxch.core.domain.utils

object AssetGlob {
    private const val MIN_BUILD_NUMBER_DIGITS = 3
    private const val MIN_COMMIT_HASH_LENGTH = 6

    fun deriveGlob(assetName: String): String? {
        val lower = assetName.lowercase()

        val versionPattern =
            Regex("""v?\d+(?:\.\d+)+|(?<![A-Za-z\d])\d{2,}(?![A-Za-z\d])""")
        if (!versionPattern.containsMatchIn(lower)) return null
        return versionPattern.replace(lower, "*")
    }

    // The tag's version without an app-name prefix (auth-v4.4.25 -> 4.4.25); null without a usable number (nightly, v2).
    fun tagVersion(tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val lower = tag.trim().lowercase()
        val start = TAG_VERSION.find(lower)?.range?.first ?: lower.indexOfFirst { it.isDigit() }
        if (start < 0) return null
        return lower.substring(start).takeIf { it.length >= 2 }
    }

    // The name with its own release's version, and any build id beside it, blanked out; null when the name lacks it.
    fun tagGlob(
        assetName: String,
        releaseTag: String?,
    ): String? {
        val version = tagVersion(releaseTag) ?: return null
        val lower = assetName.lowercase()
        val range = releaseVersionRange(lower, version) ?: return null
        return lower.replaceRange(range, "*")
    }

    // Same file family when either form agrees: the version pattern, or the name with its release's version blanked.
    fun familyGlobs(
        assetName: String,
        releaseTag: String?,
    ): Set<String> = setOfNotNull(deriveGlob(assetName), tagGlob(assetName, releaseTag))

    fun tagStem(
        assetName: String,
        releaseTag: String?,
    ): String? =
        tagGlob(assetName, releaseTag)
            ?.let { AssetVariant.extractBaseStem(it.replace('*', '-')) }
            ?.takeIf { it.isNotEmpty() }

    internal fun releaseVersionRange(
        lower: String,
        version: String,
    ): IntRange? = versionInName(version).findAll(lower).firstNotNullOfOrNull { cleanVersionRange(lower, it.range) }

    private fun cleanVersionRange(
        lower: String,
        match: IntRange,
    ): IntRange? {
        if (!startsCleanly(lower, match.first)) return null
        val end = generateSequence(match.last + 1) { buildIdAfter(lower, it) }.last()
        if (!endsCleanly(lower, end)) return null
        val start = if (hasVersionPrefix(lower, match.first)) match.first - 1 else match.first
        return (buildIdBefore(lower, start) ?: start) until end
    }

    // Any separator stands for any other, and one may sit where digits meet letters (4.5.0beta05 ships 4.5.0_beta05).
    private fun versionInName(version: String): Regex {
        val pattern = StringBuilder()
        var previous: Char? = null
        for (c in version) {
            when {
                c in BUILD_ID_SEPARATORS -> {
                    pattern.append("[._+-]")
                }

                previous != null && previous !in BUILD_ID_SEPARATORS && previous.isDigit() != c.isDigit() -> {
                    pattern.append("[._-]?").append(Regex.escape(c.toString()))
                }

                else -> {
                    pattern.append(Regex.escape(c.toString()))
                }
            }
            previous = c
        }
        return Regex(pattern.toString())
    }

    private fun startsCleanly(
        lower: String,
        at: Int,
    ): Boolean {
        val before = lower.getOrNull(at - 1) ?: return true
        if (before.isDigit()) return false
        if (before.isLetter()) return hasVersionPrefix(lower, at)
        return !(before == '.' && lower.getOrNull(at - 2)?.isDigit() == true)
    }

    // "v4.4.25": the v is part of the version when it stands alone before it.
    private fun hasVersionPrefix(
        lower: String,
        at: Int,
    ): Boolean = lower.getOrNull(at - 1) == 'v' && lower.getOrNull(at - 2)?.isLetterOrDigit() != true

    private fun endsCleanly(
        lower: String,
        end: Int,
    ): Boolean {
        val after = lower.getOrNull(end) ?: return true
        if (after.isDigit()) return false
        return !((after == '.' || after == '_') && lower.getOrNull(end + 1)?.isDigit() == true)
    }

    // End of a build id that follows the version at `from`, glued or after one separator.
    private fun buildIdAfter(
        lower: String,
        from: Int,
    ): Int? {
        val start = if (lower.getOrNull(from)?.let { it in BUILD_ID_SEPARATORS } == true) from + 1 else from
        var end = start
        while (end < lower.length && lower[end].isLetterOrDigit()) end++
        return end.takeIf { isBuildId(lower.substring(start, end)) }
    }

    // Start of a build id written just before the version, followed by one separator.
    private fun buildIdBefore(
        lower: String,
        versionAt: Int,
    ): Int? {
        if (lower.getOrNull(versionAt - 1)?.let { it in BUILD_ID_SEPARATORS } != true) return null
        val end = versionAt - 1
        var start = end
        while (start > 0 && lower[start - 1].isLetterOrDigit()) start--
        return start.takeIf { isBuildId(lower.substring(start, end)) }
    }

    // A build number (3+ digits) or short commit hash (6+ hex chars mixing letters and digits); never arm64, v8a or x86.
    private fun isBuildId(token: String): Boolean =
        (token.length >= MIN_BUILD_NUMBER_DIGITS && token.all { it.isDigit() }) ||
            (
                token.length >= MIN_COMMIT_HASH_LENGTH &&
                    token.all { it.isDigit() || it in 'a'..'f' } &&
                    token.any { it.isDigit() } &&
                    token.any { it.isLetter() }
            )

    private val TAG_VERSION = Regex("""\d+(?:[._]\d+)+""")
    private val BUILD_ID_SEPARATORS = setOf('.', '-', '_', '+')

    fun versionMajor(name: String): Int? =
        VERSION_MAJOR
            .find(name)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private val VERSION_MAJOR = Regex("""(?<![A-Za-z\d])v?(\d+)\.\d+""", RegexOption.IGNORE_CASE)
}
