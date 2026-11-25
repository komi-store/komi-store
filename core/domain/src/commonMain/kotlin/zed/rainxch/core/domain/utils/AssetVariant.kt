package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset

object AssetVariant {
    private val ARCH_TOKENS =
        setOf(
            "arm64-v8a",
            "arm64",
            "aarch64",
            "armeabi-v7a",
            "armeabi",
            "armv7",
            "armv7a",
            "armv8",
            "x86_64",
            "x86-64",
            "x64",
            "x86",
            "i386",
            "i686",
            "mips",
            "mips64",
            "universal",
            "all",
        )

    private val FLAVOR_TOKENS =
        setOf(
            "fdroid",
            "f-droid",
            "play",
            "playstore",
            "googleplay",
            "gms",
            "nogms",
            "huawei",
            "amazon",
            "samsung",
            "foss",
            "libre",
            "free",
            "pro",
            "premium",
            "full",
            "lite",
            "beta",
            "stable",
            "canary",
            "nightly",
        )

    private val VOCABULARY: Set<String> by lazy { ARCH_TOKENS + FLAVOR_TOKENS }

    private fun canonicalFlavorToken(token: String): String =
        token
            .replace('-', ' ')
            .replace('_', ' ')
            .replace(" ", "")
            .lowercase()

    private fun tokenize(assetName: String): List<String> {
        val withoutExt = assetName.substringBeforeLast('.')
        return withoutExt
            .lowercase()
            .split('-', '_', ' ', '.')
            .filter { it.isNotEmpty() }
    }

    fun extractTokens(assetName: String): Set<String> = VocabularyMatch(tokenize(assetName)).found

    fun extractBaseStem(assetName: String): String {
        val tokens = tokenize(assetName)
        val consumed = VocabularyMatch(tokens).consumed
        return tokens
            .filterIndexed { i, token -> !consumed[i] && token !in VOCABULARY && !isVersionLikeToken(token) }
            .joinToString("")
    }

    // Longer runs claim their tokens first; a two-token run may be written with either separator.
    private val RUN_SEPARATORS = listOf(3 to listOf("-"), 2 to listOf("-", "_"), 1 to listOf(""))

    private class VocabularyMatch(
        private val tokens: List<String>,
    ) {
        val found = mutableSetOf<String>()
        val consumed = BooleanArray(tokens.size)

        init {
            RUN_SEPARATORS.forEach { (length, separators) -> claimRuns(length, separators) }
        }

        private fun claimRuns(
            length: Int,
            separators: List<String>,
        ) {
            for (start in 0..tokens.size - length) {
                val match = matchAt(start, length, separators) ?: continue
                found += match
                for (i in start until start + length) consumed[i] = true
            }
        }

        private fun matchAt(
            start: Int,
            length: Int,
            separators: List<String>,
        ): String? {
            if ((start until start + length).any { consumed[it] }) return null
            val run = tokens.subList(start, start + length)
            return separators.map { run.joinToString(it) }.firstOrNull { it in VOCABULARY }
        }
    }

    private fun isVersionLikeToken(token: String): Boolean {
        if (token.isEmpty()) return false
        if (token.all { it.isDigit() }) return true
        if (token.startsWith("v") && token.drop(1).all { it.isDigit() }) return true
        return false
    }

    fun extract(
        assetName: String,
        releaseTag: String? = null,
    ): String? {
        val withoutExt = assetName.substringBeforeLast('.')
        val tagRange = AssetGlob.tagVersion(releaseTag)?.let { AssetGlob.releaseVersionRange(withoutExt.lowercasePerChar(), it) }
        val tailStart =
            tagRange?.let { it.last + 1 }
                ?: VERSION_SEGMENT.find(withoutExt)?.let { it.range.last + 1 }
                ?: return null
        var tail =
            withoutExt
                .substring(tailStart)
                .trimStart { it in LEADING_SEPARATORS }
                .trim()

        while (true) {
            val qmatch = PRE_RELEASE_PREFIX.find(tail) ?: break
            tail =
                tail
                    .substring(qmatch.range.last + 1)
                    .trimStart { it in LEADING_SEPARATORS }
                    .trim()
        }
        return tail
    }

    // String.lowercase() can lengthen a name ('İ' becomes two chars), shifting indices into the original.
    private fun String.lowercasePerChar(): String = String(CharArray(length) { this[it].lowercaseChar() })

    private val VERSION_SEGMENT =
        Regex("[-_ ]v?\\d+(?:\\.\\d+)+(?=[-_. ]|$)", RegexOption.IGNORE_CASE)

    private val PRE_RELEASE_PREFIX =
        Regex(
            "^(beta|rc|alpha|dev|nightly|snapshot|pre|preview|m|milestone|pr)[.\\-_]?\\d+(?=[-_. ]|$)",
            RegexOption.IGNORE_CASE,
        )

    private val LEADING_SEPARATORS = setOf('-', '_', ' ', '.')

    fun resolvePreferredAsset(
        assets: List<GithubAsset>,
        pinnedVariant: String?,
        pinnedTokens: Set<String>? = null,
        pinnedGlob: String? = null,
        releaseTag: String? = null,
    ): GithubAsset? =
        pinnedTokens
            ?.takeIf { it.isNotEmpty() }
            ?.let { tokens -> assets.firstOrNull { extractTokens(it.name) == tokens } }
            ?: pinnedGlob
                ?.takeIf { it.isNotBlank() }
                ?.let { glob -> assets.firstOrNull { glob in AssetGlob.familyGlobs(it.name, releaseTag) } }
            ?: pinnedVariant
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { target -> assets.firstOrNull { it.hasVariant(target, releaseTag) } }

    private fun GithubAsset.hasVariant(
        target: String,
        releaseTag: String?,
    ): Boolean =
        extract(name)?.equals(target, ignoreCase = true) == true ||
            extract(name, releaseTag)?.equals(target, ignoreCase = true) == true

    fun resolveBySamePosition(
        assets: List<GithubAsset>,
        originalIndex: Int?,
        siblingCountAtPickTime: Int?,
    ): GithubAsset? {
        if (originalIndex == null || siblingCountAtPickTime == null) return null
        if (siblingCountAtPickTime <= 0) return null
        if (assets.size != siblingCountAtPickTime) return null
        return assets.getOrNull(originalIndex)
    }

    fun deriveFromPickedAsset(
        pickedAssetName: String,
        siblingAssetCount: Int,
        releaseTag: String? = null,
    ): String? {
        if (siblingAssetCount <= 1) return null
        val variant = extract(pickedAssetName, releaseTag) ?: return null
        return variant.takeIf { it.isNotEmpty() }
    }

    data class VariantFingerprint(
        val variant: String?,
        val tokens: Set<String>,
        val glob: String?,
    )

    fun fingerprintFromPickedAsset(
        pickedAssetName: String,
        siblingAssetCount: Int,
        releaseTag: String? = null,
    ): VariantFingerprint? {
        if (siblingAssetCount <= 1) return null
        val variant = extract(pickedAssetName, releaseTag)?.takeIf { it.isNotEmpty() }
        val tokens = extractTokens(pickedAssetName)
        val glob = AssetGlob.tagGlob(pickedAssetName, releaseTag) ?: AssetGlob.deriveGlob(pickedAssetName)

        if (variant == null && tokens.isEmpty() && glob == null) return null
        return VariantFingerprint(variant = variant, tokens = tokens, glob = glob)
    }

    fun serializeTokens(tokens: Set<String>): String? {
        if (tokens.isEmpty()) return null
        return tokens.sorted().joinToString("|")
    }

    fun deserializeTokens(serialized: String?): Set<String> {
        if (serialized.isNullOrBlank()) return emptySet()
        return serialized.split('|').filter { it.isNotBlank() }.toSet()
    }

    fun filterByPackageFlavor(
        assets: List<GithubAsset>,
        trackedPackageName: String,
    ): List<GithubAsset> {
        if (assets.isEmpty()) return assets
        val packageSegments =
            trackedPackageName.lowercase().split('.').filter { it.isNotBlank() }

        val packageFlavorTokens =
            packageSegments
                .filter { it in FLAVOR_TOKENS }
                .map(::canonicalFlavorToken)
                .toSet()

        return if (packageFlavorTokens.isNotEmpty()) {
            val matching =
                assets.filter { asset ->
                    extractTokens(asset.name).any { canonicalFlavorToken(it) in packageFlavorTokens }
                }
            matching.ifEmpty { assets }
        } else {
            val unflavoured =
                assets.filter { asset ->
                    extractTokens(asset.name).none { it in FLAVOR_TOKENS }
                }
            unflavoured.ifEmpty { assets }
        }
    }
}
