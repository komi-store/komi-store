package zed.rainxch.core.data.mappers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.core.domain.model.transfer.ExportedApp
import zed.rainxch.core.domain.model.transfer.ObtainiumApp

private data class AdditionalSettingsParse(
    val filter: String?,
    val fallbackToOlderReleases: Boolean,
    val invertFilter: Boolean,
)

// GitHub's own limits on owner and repository names.
private const val MAX_OWNER_LENGTH = 39
private const val MAX_REPO_LENGTH = 100

fun ObtainiumApp.toExportedAppOrSkip(json: Json): ObtainiumMapResult {
    val rawUrl = url.trim()
    val (owner, repo) =
        parseGithubOwnerRepo(rawUrl)
            ?: return ObtainiumMapResult(
                exported = null,
                nonGitHubLabel =
                    (name?.takeIf { it.isNotBlank() } ?: id.ifBlank { rawUrl }) +
                        if (rawUrl.isNotEmpty()) " ($rawUrl)" else "",
                unsupportedFailureLabel = null,
            )
    val packageName = id.trim().takeIf { it.isNotBlank() }
    // The settings are parsed only for an entry with an id: a malformed value there throws.
    return if (packageName == null) {
        unsupported("$owner/$repo (missing package id)")
    } else {
        exportOrUnsupported(packageName, owner, repo, parseAdditionalSettings(additionalSettingsRaw, json))
    }
}

private fun ObtainiumApp.exportOrUnsupported(
    packageName: String,
    owner: String,
    repo: String,
    parsed: AdditionalSettingsParse,
): ObtainiumMapResult =
    if (parsed.invertFilter) {
        unsupported("$owner/$repo (inverted APK filter — not supported)")
    } else {
        ObtainiumMapResult(
            exported =
                ExportedApp(
                    packageName = packageName,
                    repoOwner = owner,
                    repoName = repo,
                    repoUrl = "https://github.com/$owner/$repo",
                    assetFilterRegex = parsed.filter,
                    fallbackToOlderReleases = parsed.fallbackToOlderReleases,
                    preferredAssetVariant = null,
                    preferredAssetTokens = null,
                    assetGlobPattern = null,
                    pickedAssetIndex = preferredApkIndex,
                    pickedAssetSiblingCount = null,
                ),
            nonGitHubLabel = null,
            unsupportedFailureLabel = null,
        )
    }

private fun unsupported(label: String) =
    ObtainiumMapResult(
        exported = null,
        nonGitHubLabel = null,
        unsupportedFailureLabel = label,
    )

private fun parseGithubOwnerRepo(rawUrl: String): Pair<String, String>? {
    if (rawUrl.isBlank()) return null
    val parts = githubRepoPath(rawUrl)?.split('/') ?: return null
    val owner = parts[0]
    val repo = parts.getOrNull(1).orEmpty()
    val named = owner.isNotBlank() && repo.isNotBlank()
    val withinLimits = owner.length <= MAX_OWNER_LENGTH && repo.length <= MAX_REPO_LENGTH
    return if (named && withinLimits) owner to repo else null
}

// The part after github.com, or null for a URL on another host.
private fun githubRepoPath(rawUrl: String): String? {
    val trimmed = rawUrl.substringBefore('?').substringBefore('#').trimEnd('/')
    val withoutScheme = trimmed.removePrefix("https://").removePrefix("http://")
    val withoutHost =
        withoutScheme
            .removePrefix("www.")
            .removePrefix("github.com/")
    return when {
        withoutScheme.startsWith("github.com") -> withoutScheme.removePrefix("github.com").trimStart('/')
        withoutScheme == withoutHost -> null
        else -> withoutHost
    }
}

private fun parseAdditionalSettings(
    additional: kotlinx.serialization.json.JsonElement?,
    json: Json,
): AdditionalSettingsParse {
    if (additional == null) return AdditionalSettingsParse(null, false, false)
    val obj =
        when (additional) {
            is JsonPrimitive -> {
                additional.contentOrNull?.let {
                    runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
                }
            }

            else -> {
                runCatching { additional.jsonObject }.getOrNull()
            }
        } ?: return AdditionalSettingsParse(null, false, false)

    val filter = obj["apkFilterRegEx"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    val invertFilter = obj["invertAPKFilter"]?.jsonPrimitive?.runCatching { boolean }?.getOrNull() == true
    val fallback = obj["fallbackToOlderReleases"]?.jsonPrimitive?.runCatching { boolean }?.getOrNull() == true

    return AdditionalSettingsParse(filter, fallback, invertFilter)
}
