package zed.rainxch.core.data.mappers

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.transfer.ObtainiumApp

fun InstalledApp.toObtainiumApp(): ObtainiumApp {
    val additional: JsonElement =
        buildJsonObject {
            if (!assetFilterRegex.isNullOrBlank()) {
                put("apkFilterRegEx", JsonPrimitive(assetFilterRegex))
                put("invertAPKFilter", JsonPrimitive(false))
            }
            put("fallbackToOlderReleases", JsonPrimitive(fallbackToOlderReleases))
            put("trackOnly", JsonPrimitive(false))
            put("dontSortReleasesList", JsonPrimitive(false))
        }

    return ObtainiumApp(
        id = packageName,
        url = repoUrl,
        author = repoOwner,
        name = repoName,
        installedVersion = installedVersion,
        latestVersion = latestVersion,
        preferredApkIndex = pickedAssetIndex,
        pinned = false,
        categories = emptyList(),
        additionalSettingsRaw = additional,
        overrideSource = "GitHub",
    )
}
