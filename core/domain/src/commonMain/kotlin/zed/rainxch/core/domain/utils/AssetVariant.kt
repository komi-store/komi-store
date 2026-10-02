package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.installation.InstalledApp

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
        token.replace('-', ' ').replace('_', ' ').replace(" ", "").lowercase()

    private fun tokenize(assetName: String): List<String> {
        val withoutExt = assetName.substringBeforeLast('.')
        return withoutExt
            .lowercase()
            .split('-', '_', ' ', '.')
            .filter { it.isNotEmpty() }
    }

    fun extractTokens(assetName: String): Set<String> {
        val tokens = tokenize(assetName)
        if (tokens.isEmpty()) return emptySet()

        val found = mutableSetOf<String>()

        val consumed = BooleanArray(tokens.size)

        for (i in 0 until tokens.size - 2) {
            if (consumed[i] || consumed[i + 1] || consumed[i + 2]) continue
            val candidate = "${tokens[i]}-${tokens[i + 1]}-${tokens[i + 2]}"
            if (candidate in VOCABULARY) {
                found += candidate
                consumed[i] = true
                consumed[i + 1] = true
                consumed[i + 2] = true
            }
        }

        for (i in 0 until tokens.size - 1) {
            if (consumed[i] || consumed[i + 1]) continue
            val dashed = "${tokens[i]}-${tokens[i + 1]}"
            val underscored = "${tokens[i]}_${tokens[i + 1]}"
            val match =
                when {
                    dashed in VOCABULARY -> dashed
                    underscored in VOCABULARY -> underscored
                    else -> null
                }
            if (match != null) {
                found += match
                consumed[i] = true
                consumed[i + 1] = true
            }
        }

        for (i in tokens.indices) {
            if (consumed[i]) continue
            if (tokens[i] in VOCABULARY) {
                found += tokens[i]
                consumed[i] = true
            }
        }

        return found
    }

    fun extractBaseStem(assetName: String): String {
        val tokens = tokenize(assetName)
        if (tokens.isEmpty()) return ""

        val consumed = BooleanArray(tokens.size)

        for (i in 0 until tokens.size - 2) {
            if (consumed[i] || consumed[i + 1] || consumed[i + 2]) continue
            val candidate = "${tokens[i]}-${tokens[i + 1]}-${tokens[i + 2]}"
            if (candidate in VOCABULARY) {
                consumed[i] = true; consumed[i + 1] = true; consumed[i + 2] = true
            }
        }
        for (i in 0 until tokens.size - 1) {
            if (consumed[i] || consumed[i + 1]) continue
            val dashed = "${tokens[i]}-${tokens[i + 1]}"
            val underscored = "${tokens[i]}_${tokens[i + 1]}"
            if (dashed in VOCABULARY || underscored in VOCABULARY) {
                consumed[i] = true; consumed[i + 1] = true
            }
        }

        val out = StringBuilder()
        for (i in tokens.indices) {
            if (consumed[i]) continue
            val t = tokens[i]
            if (t in VOCABULARY) continue
            if (isVersionLikeToken(t)) continue
            out.append(t)
        }
        return out.toString()
    }

    private fun isVersionLikeToken(token: String): Boolean {
        if (token.isEmpty()) return false
        if (token.all { it.isDigit() }) return true
        if (token.startsWith("v") && token.drop(1).all { it.isDigit() }) return true
        return false
    }

    fun deriveGlob(assetName: String): String? {
        val lower = assetName.lowercase()

        val versionPattern =
            Regex("""v?\d+(?:\.\d+)+|(?<![A-Za-z\d])\d{2,}(?![A-Za-z\d])""")
        if (!versionPattern.containsMatchIn(lower)) return null
        return versionPattern.replace(lower, "*")
    }

    fun extract(assetName: String): String? {
        val withoutExt = assetName.substringBeforeLast('.')
        val match = VERSION_SEGMENT.find(withoutExt) ?: return null
        var tail =
            withoutExt
                .substring(match.range.last + 1)
                .trimStart(*LEADING_SEPARATORS)
                .trim()

        while (true) {
            val qmatch = PRE_RELEASE_PREFIX.find(tail) ?: break
            tail = tail.substring(qmatch.range.last + 1)
                .trimStart(*LEADING_SEPARATORS)
                .trim()
        }
        return tail
    }

    private val VERSION_SEGMENT =
        Regex("[-_ ]v?\\d+(?:\\.\\d+)+(?=[-_. ]|$)", RegexOption.IGNORE_CASE)

    private val PRE_RELEASE_PREFIX =
        Regex(
            "^(beta|rc|alpha|dev|nightly|snapshot|pre|preview|m|milestone|pr)[.\\-_]?\\d+(?=[-_. ]|$)",
            RegexOption.IGNORE_CASE,
        )

    private val LEADING_SEPARATORS = charArrayOf('-', '_', ' ', '.')

    fun resolvePreferredAsset(
        assets: List<GithubAsset>,
        pinnedVariant: String?,
        pinnedTokens: Set<String>? = null,
        pinnedGlob: String? = null,
    ): GithubAsset? {

        if (!pinnedTokens.isNullOrEmpty()) {
            val match = assets.firstOrNull { asset ->
                extractTokens(asset.name) == pinnedTokens
            }
            if (match != null) return match
        }

        if (!pinnedGlob.isNullOrBlank()) {
            val match = assets.firstOrNull { asset ->
                deriveGlob(asset.name) == pinnedGlob
            }
            if (match != null) return match
        }

        val target = pinnedVariant?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return assets.firstOrNull { asset ->
            extract(asset.name)?.equals(target, ignoreCase = true) == true
        }
    }

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
    ): String? {
        if (siblingAssetCount <= 1) return null
        val variant = extract(pickedAssetName) ?: return null
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
    ): VariantFingerprint? {
        if (siblingAssetCount <= 1) return null
        val variant = extract(pickedAssetName)?.takeIf { it.isNotEmpty() }
        val tokens = extractTokens(pickedAssetName)
        val glob = deriveGlob(pickedAssetName)

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

        val packageFlavorTokens = packageSegments.filter { it in FLAVOR_TOKENS }
            .map(::canonicalFlavorToken)
            .toSet()

        return if (packageFlavorTokens.isNotEmpty()) {
            val matching = assets.filter { asset ->
                extractTokens(asset.name).any { canonicalFlavorToken(it) in packageFlavorTokens }
            }
            matching.ifEmpty { assets }
        } else {
            val unflavoured = assets.filter { asset ->
                extractTokens(asset.name).none { it in FLAVOR_TOKENS }
            }
            unflavoured.ifEmpty { assets }
        }
    }

    fun findMatchingInstalledApp(
        apps: List<InstalledApp>,
        assetName: String?,
    ): InstalledApp? {
        if (apps.isEmpty()) return null
        if (assetName == null) {
            return apps.firstOrNull { !it.isUpdateAvailable } ?: apps.first()
        }

        val filterMatch = apps.firstOrNull { existing ->
            val filter = existing.assetFilterRegex
            filter != null && runCatching { Regex(filter).containsMatchIn(assetName) }
                .getOrDefault(false)
        }
        if (filterMatch != null) return filterMatch

        if (apps.size == 1) {
            val sole = apps.first()
            if (sole.assetFilterRegex != null) {
                return null
            }
            if (sole.installedAssetName != null) {
                val installedName = sole.installedAssetName
                val soleStem = extractBaseStem(installedName)
                val primaryStem = extractBaseStem(assetName)
                if (soleStem.isNotEmpty() && primaryStem.isNotEmpty() && soleStem != primaryStem) {
                    return null
                }
                val soleGlob = deriveGlob(installedName)
                val primaryGlob = deriveGlob(assetName)
                if (soleGlob != null && primaryGlob != null && soleGlob != primaryGlob && soleStem.isEmpty()) {
                    return null
                }
            }
            return sole
        }

        val primaryGlob = deriveGlob(assetName)
        val globMatch = apps.firstOrNull { existing ->
            val installedAsset = existing.installedAssetName ?: return@firstOrNull false
            val existingGlob = deriveGlob(installedAsset)
            existingGlob != null && primaryGlob != null && existingGlob == primaryGlob
        }
        if (globMatch != null) return globMatch

        val primaryStem = extractBaseStem(assetName)
        if (primaryStem.isNotEmpty()) {
            val stemMatch = apps.firstOrNull { existing ->
                val name = existing.installedAssetName ?: return@firstOrNull false
                val existingStem = extractBaseStem(name)
                existingStem.isNotEmpty() && existingStem == primaryStem
            }
            if (stemMatch != null) return stemMatch
        }
        return null
    }
}
