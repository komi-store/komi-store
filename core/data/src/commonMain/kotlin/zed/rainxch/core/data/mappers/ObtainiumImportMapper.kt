package zed.rainxch.core.data.mappers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.core.domain.model.transfer.ExportedApp
import zed.rainxch.core.domain.model.transfer.ObtainiumApp

data class ObtainiumMapResult(
    val exported: ExportedApp?,
    val nonGitHubLabel: String?,
    val unsupportedFailureLabel: String?,
)

private data class AdditionalSettingsParse(
    val filter: String?,
    val fallbackToOlderReleases: Boolean,
    val invertFilter: Boolean,
)

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

    val packageName =
        id.trim().takeIf { it.isNotBlank() } ?: return ObtainiumMapResult(
            exported = null,
            nonGitHubLabel = null,
            unsupportedFailureLabel = "$owner/$repo (missing package id)",
        )

    val canonicalUrl = "https://github.com/$owner/$repo"
    val parsed = parseAdditionalSettings(additionalSettingsRaw, json)
    if (parsed.invertFilter) {
        return ObtainiumMapResult(
            exported = null,
            nonGitHubLabel = null,
            unsupportedFailureLabel = "$owner/$repo (inverted APK filter — not supported)",
        )
    }

    return ObtainiumMapResult(
        exported =
            ExportedApp(
                packageName = packageName,
                repoOwner = owner,
                repoName = repo,
                repoUrl = canonicalUrl,
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

private fun parseGithubOwnerRepo(rawUrl: String): Pair<String, String>? {
    if (rawUrl.isBlank()) return null
    val trimmed = rawUrl.substringBefore('?').substringBefore('#').trimEnd('/')
    val withoutScheme = trimmed.removePrefix("https://").removePrefix("http://")
    val withoutHost =
        withoutScheme
            .removePrefix("www.")
            .removePrefix("github.com/")
    if (withoutScheme == withoutHost && !withoutScheme.startsWith("github.com")) return null
    val cleaned =
        if (withoutScheme.startsWith("github.com")) {
            withoutScheme.removePrefix("github.com").trimStart('/')
        } else {
            withoutHost
        }
    val parts = cleaned.split('/')
    if (parts.size < 2) return null
    val owner = parts[0]
    val repo = parts[1]
    if (owner.isBlank() || repo.isBlank()) return null
    if (owner.length > 39 || repo.length > 100) return null
    return owner to repo
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
