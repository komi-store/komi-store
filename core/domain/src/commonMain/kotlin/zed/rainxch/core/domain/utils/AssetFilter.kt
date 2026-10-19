package zed.rainxch.core.domain.utils

class AssetFilter private constructor(
    val pattern: String,
    private val regex: Regex,
) {
    fun matches(assetName: String): Boolean = regex.containsMatchIn(assetName)

    override fun equals(other: Any?): Boolean = other is AssetFilter && other.pattern == pattern

    override fun hashCode(): Int = pattern.hashCode()

    override fun toString(): String = "AssetFilter($pattern)"

    companion object {
        fun parse(raw: String?): Result<AssetFilter>? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return runCatching {
                AssetFilter(pattern = trimmed, regex = Regex(trimmed, RegexOption.IGNORE_CASE))
            }
        }

        fun suggestFromAssetName(assetName: String): String? {
            val versionAnchor = Regex("[-_ .]\\d")
            val match = versionAnchor.find(assetName) ?: return null
            val prefix = assetName.substring(0, match.range.first + 1)

            if (prefix.length < 2) return null
            return "^" + Regex.escape(prefix)
        }
    }
}
