package zed.rainxch.apps.data.repository

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.apps.domain.model.ImportFormat
import zed.rainxch.apps.domain.model.ImportResult
import zed.rainxch.apps.domain.repository.AppLinking
import zed.rainxch.apps.domain.repository.AppsBackup
import zed.rainxch.apps.domain.repository.RepoLookup
import zed.rainxch.core.data.mappers.toExportedAppOrSkip
import zed.rainxch.core.data.mappers.toObtainiumApp
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.transfer.ExportedApp
import zed.rainxch.core.domain.model.transfer.ExportedAppList
import zed.rainxch.core.domain.model.transfer.ObtainiumExport
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.PackageMonitor
import kotlin.time.Clock

internal class AppsBackupImpl(
    private val installedApps: InstalledAppsRepository,
    private val packageMonitor: PackageMonitor,
    private val lookup: RepoLookup,
    private val linking: AppLinking,
    private val logger: KomiStoreLogger,
) : AppsBackup {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exportApps(): String {
        val apps = installedApps.getAllInstalledApps().first()
        val exported =
            ExportedAppList(
                version = NATIVE_EXPORT_VERSION,
                exportedAt = Clock.System.now().toEpochMilliseconds(),
                apps =
                    apps.map { app ->
                        ExportedApp(
                            packageName = app.packageName,
                            repoOwner = app.repoOwner,
                            repoName = app.repoName,
                            repoUrl = app.repoUrl,
                            assetFilterRegex = app.assetFilterRegex,
                            fallbackToOlderReleases = app.fallbackToOlderReleases,
                            preferredAssetVariant = app.preferredAssetVariant,
                            preferredAssetTokens = app.preferredAssetTokens,
                            assetGlobPattern = app.assetGlobPattern,
                            pickedAssetIndex = app.pickedAssetIndex,
                            pickedAssetSiblingCount = app.pickedAssetSiblingCount,
                        )
                    },
            )
        return json.encodeToString(ExportedAppList.serializer(), exported)
    }

    override suspend fun exportObtainium(): String {
        val apps = installedApps.getAllInstalledApps().first()
        val export =
            ObtainiumExport(
                apps = apps.map { it.toObtainiumApp() },
                overrideExportFormatVersion = OBTAINIUM_EXPORT_FORMAT_VERSION,
            )
        return json.encodeToString(ObtainiumExport.serializer(), export)
    }

    override suspend fun importApps(json: String): ImportResult {
        val parsed =
            try {
                this.json.parseToJsonElement(json)
            } catch (e: SerializationException) {
                logger.error("Import: not valid JSON: ${e.message}")
                return unknownFormat(json)
            }
        return when (detectFormat(parsed)) {
            ImportFormat.NATIVE -> importNative(json)
            ImportFormat.OBTAINIUM -> importObtainium(json)
            ImportFormat.UNKNOWN -> unknownFormat(json)
        }
    }

    private fun unknownFormat(rawJson: String): ImportResult =
        ImportResult(
            failed = 1,
            sourceFormat = ImportFormat.UNKNOWN,
            unknownFormatPreview = rawJson.take(UNKNOWN_PREVIEW_CHARS),
        )

    private fun detectFormat(element: JsonElement): ImportFormat {
        val root = element as? JsonObject ?: return ImportFormat.UNKNOWN
        val apps = root["apps"] as? JsonArray ?: return ImportFormat.UNKNOWN
        val entries = apps.filterIsInstance<JsonObject>()
        return when {
            entries.any { it.containsKey("repoOwner") && it.containsKey("repoName") } -> ImportFormat.NATIVE
            entries.any { it.isObtainiumGithubEntry() } -> ImportFormat.OBTAINIUM
            apps.isEmpty() -> emptyExportFormat(root)
            else -> ImportFormat.UNKNOWN
        }
    }

    private fun JsonObject.isObtainiumGithubEntry(): Boolean =
        containsKey("id") &&
            containsKey("url") &&
            this["url"]?.jsonPrimitive?.contentOrNull?.contains("github.com", ignoreCase = true) == true

    // An export with no apps still says which app wrote it: a numeric version is ours, settings are Obtainium's.
    private fun emptyExportFormat(root: JsonObject): ImportFormat {
        val versionNode = root["version"]
        return when {
            versionNode is JsonPrimitive && versionNode.contentOrNull?.toIntOrNull() != null -> ImportFormat.NATIVE
            root.containsKey("overrideExportFormatVersion") || root.containsKey("settings") -> ImportFormat.OBTAINIUM
            else -> ImportFormat.UNKNOWN
        }
    }

    private suspend fun importNative(rawJson: String): ImportResult {
        val exportedList =
            try {
                json.decodeFromString(ExportedAppList.serializer(), rawJson)
            } catch (e: IllegalArgumentException) {
                logger.error("Failed to parse native import JSON: ${e.message}")
                return ImportResult(failed = 1, sourceFormat = ImportFormat.NATIVE)
            }

        val trackedPackages = linking.getTrackedPackageNames()
        val tally = ImportTally()
        for (exportedApp in exportedList.apps) {
            tally.importUnlessTracked(exportedApp, exportedApp.repoName, trackedPackages)
        }
        return tally.toResult(ImportFormat.NATIVE)
    }

    private suspend fun importObtainium(rawJson: String): ImportResult {
        val export =
            try {
                json.decodeFromString(ObtainiumExport.serializer(), rawJson)
            } catch (e: IllegalArgumentException) {
                logger.error("Failed to parse Obtainium import JSON: ${e.message}")
                return ImportResult(failed = 1, sourceFormat = ImportFormat.OBTAINIUM)
            }

        val trackedPackages = linking.getTrackedPackageNames()
        val tally = ImportTally()
        for (obtainiumApp in export.apps) {
            val mapped = obtainiumApp.toExportedAppOrSkip(json)
            val exportedApp = mapped.exported
            if (exportedApp == null) {
                tally.nonGitHub += listOfNotNull(mapped.nonGitHubLabel)
                tally.failed += listOfNotNull(mapped.unsupportedFailureLabel)
            } else {
                val appName = obtainiumApp.name?.takeIf { it.isNotBlank() } ?: exportedApp.repoName
                tally.importUnlessTracked(exportedApp, appName, trackedPackages)
            }
        }
        return tally.toResult(ImportFormat.OBTAINIUM)
    }

    private suspend fun ImportTally.importUnlessTracked(
        exportedApp: ExportedApp,
        appName: String,
        trackedPackages: Set<String>,
    ) {
        val label = "${exportedApp.repoOwner}/${exportedApp.repoName}"
        if (exportedApp.packageName in trackedPackages) {
            skipped += label
        } else {
            record(label, importApp(exportedApp, appName))
        }
    }

    // A row means an installed app, so a package missing from this phone is reported, not imported.
    private suspend fun importApp(
        exportedApp: ExportedApp,
        appName: String,
    ): ImportOutcome =
        try {
            linkExported(exportedApp, appName)
        } catch (e: RateLimitException) {
            importFailed(exportedApp, e)
        } catch (e: LocalStoreException) {
            importFailed(exportedApp, e)
        } catch (e: SystemPackagesException) {
            importFailed(exportedApp, e)
        }

    private suspend fun linkExported(
        exportedApp: ExportedApp,
        appName: String,
    ): ImportOutcome {
        val systemInfo = packageMonitor.getInstalledPackageInfo(exportedApp.packageName)
        val repoInfo = systemInfo?.let { lookup.fetchRepoInfo(exportedApp.repoOwner, exportedApp.repoName) }
        return when {
            systemInfo == null -> {
                ImportOutcome.NotInstalled
            }

            repoInfo == null -> {
                ImportOutcome.Failed
            }

            else -> {
                val deviceApp =
                    DeviceApp(
                        packageName = exportedApp.packageName,
                        appName = appName,
                        versionName = systemInfo.versionName,
                        versionCode = systemInfo.versionCode,
                        signingFingerprint = systemInfo.signingFingerprint,
                    )
                linking.linkAppToRepo(
                    deviceApp = deviceApp,
                    repoInfo = repoInfo,
                    assetFilterRegex = exportedApp.assetFilterRegex,
                    fallbackToOlderReleases = exportedApp.fallbackToOlderReleases,
                    pickedAssetSiblingCount = exportedApp.pickedAssetSiblingCount ?: 0,
                    preferredAssetVariant = exportedApp.preferredAssetVariant,
                    preferredAssetTokens = exportedApp.preferredAssetTokens,
                    assetGlobPattern = exportedApp.assetGlobPattern,
                    pickedAssetIndex = exportedApp.pickedAssetIndex,
                )
                ImportOutcome.Imported
            }
        }
    }

    private fun importFailed(
        exportedApp: ExportedApp,
        cause: Exception,
    ): ImportOutcome {
        logger.error("Failed to import ${exportedApp.repoOwner}/${exportedApp.repoName}: ${cause.message}", cause)
        return ImportOutcome.Failed
    }

    private enum class ImportOutcome { Imported, NotInstalled, Failed }

    private class ImportTally {
        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val notInstalled = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val nonGitHub = mutableListOf<String>()

        fun record(
            label: String,
            outcome: ImportOutcome,
        ) {
            when (outcome) {
                ImportOutcome.Imported -> imported += label
                ImportOutcome.NotInstalled -> notInstalled += label
                ImportOutcome.Failed -> failed += label
            }
        }

        fun toResult(format: ImportFormat): ImportResult =
            ImportResult(
                imported = imported.size,
                skipped = skipped.size,
                failed = failed.size,
                nonGitHubSkipped = nonGitHub.size,
                notInstalled = notInstalled.size,
                importedItems = imported.toImmutableList(),
                skippedItems = skipped.toImmutableList(),
                nonGitHubItems = nonGitHub.toImmutableList(),
                notInstalledItems = notInstalled.toImmutableList(),
                failedItems = failed.toImmutableList(),
                sourceFormat = format,
            )
    }

    private companion object {
        const val UNKNOWN_PREVIEW_CHARS = 200
        const val OBTAINIUM_EXPORT_FORMAT_VERSION = 1
        const val NATIVE_EXPORT_VERSION = 4
    }
}
