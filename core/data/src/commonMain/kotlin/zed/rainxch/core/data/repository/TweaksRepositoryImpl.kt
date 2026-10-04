package zed.rainxch.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.secure.MigrationEntry
import zed.rainxch.core.data.secure.migrateDataStoreToKSafe
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.domain.model.settings.AppLanguages
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.AppTheme
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.appearance.FontTheme
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.model.settings.TranslationProvider
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.data.secure.safeDelete
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safeGetFlow
import zed.rainxch.core.data.secure.safePut

class TweaksRepositoryImpl(
    private val ksafe: KSafe,
    private val legacyDataStore: DataStore<Preferences>,
) : TweaksRepository {

    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val migrationDeferred = CompletableDeferred<Unit>()
    private val rmwLock = Mutex()

    init {
        migrationScope.launch {
            runCatching {
                migrateDataStoreToKSafe(
                    legacy = legacyDataStore,
                    ksafe = ksafe,
                    markerKey = MIGRATION_MARKER,
                    entries = legacyEntries(),
                )
            }
            migrationDeferred.complete(Unit)
        }
    }

    private inline fun <reified T> gatedGetFlow(key: String, default: T): Flow<T> = flow {
        migrationDeferred.await()
        emitAll(ksafe.safeGetFlow(key, default))
    }

    override fun getThemeColor(): Flow<AppTheme> =
        gatedGetFlow(K_THEME, "").map { AppTheme.fromName(it.ifEmpty { null }) }

    override suspend fun setThemeColor(theme: AppTheme) {
        migrationDeferred.await()
        ksafe.safePut(K_THEME, theme.name)
    }

    override fun getIsDarkTheme(): Flow<Boolean?> =
        gatedGetFlow<Boolean?>(K_IS_DARK, null)

    override suspend fun setDarkTheme(isDarkTheme: Boolean?) {
        migrationDeferred.await()
        if (isDarkTheme == null) ksafe.safeDelete(K_IS_DARK) else ksafe.safePut(K_IS_DARK, isDarkTheme)
    }

    override fun getAmoledTheme(): Flow<Boolean> = gatedGetFlow(K_AMOLED, false)
    override suspend fun setAmoledTheme(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_AMOLED, enabled) }

    override fun getMangaPaper(): Flow<MangaPaperId> =
        gatedGetFlow(K_MANGA_PAPER, "").map { MangaPaperId.fromName(it.ifEmpty { null }) }

    override suspend fun setMangaPaper(paper: MangaPaperId) {
        migrationDeferred.await()
        ksafe.safePut(K_MANGA_PAPER, paper.name)
    }

    override fun getFontTheme(): Flow<FontTheme> =
        gatedGetFlow(K_FONT, "").map { FontTheme.fromName(it.ifEmpty { null }) }

    override suspend fun setFontTheme(fontTheme: FontTheme) {
        migrationDeferred.await()
        ksafe.safePut(K_FONT, fontTheme.name)
    }

    override fun getPersonality(): Flow<AppPersonality> =
        gatedGetFlow(K_PERSONALITY, "").map { AppPersonality.fromName(it.ifEmpty { null }) }

    override suspend fun setPersonality(personality: AppPersonality) {
        migrationDeferred.await()
        ksafe.safePut(K_PERSONALITY, personality.name)
    }

    override fun getAccentId(): Flow<AccentId> =
        gatedGetFlow(K_ACCENT, "").map { AccentId.fromName(it.ifEmpty { null }) }

    override suspend fun setAccentId(accentId: AccentId) {
        migrationDeferred.await()
        ksafe.safePut(K_ACCENT, accentId.name)
    }

    override fun getAutoDetectClipboardLinks(): Flow<Boolean> = gatedGetFlow(K_AUTO_DETECT_CLIPBOARD, false)
    override suspend fun setAutoDetectClipboardLinks(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_AUTO_DETECT_CLIPBOARD, enabled) }

    override fun getInstallerType(): Flow<InstallerType> =
        gatedGetFlow(K_INSTALLER_TYPE, "").map { InstallerType.fromName(it.ifEmpty { null }) }

    override suspend fun setInstallerType(type: InstallerType) {
        migrationDeferred.await()
        ksafe.safePut(K_INSTALLER_TYPE, type.name)
    }

    override fun getInstallerAttribution(): Flow<zed.rainxch.core.domain.model.installation.InstallerAttribution> =
        gatedGetFlow(K_INSTALLER_ATTRIBUTION, "").map { decodeInstallerAttribution(it.ifEmpty { null }) }

    override suspend fun setInstallerAttribution(
        attribution: zed.rainxch.core.domain.model.installation.InstallerAttribution,
    ) {
        migrationDeferred.await()
        ksafe.safePut(K_INSTALLER_ATTRIBUTION, encodeInstallerAttribution(attribution))
    }

    private fun decodeInstallerAttribution(
        raw: String?,
    ): zed.rainxch.core.domain.model.installation.InstallerAttribution {
        if (raw.isNullOrBlank()) return zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault
        val parts = raw.split(":", limit = 2)
        return when (parts[0]) {
            "preset" -> {
                val key = parts.getOrNull(1)?.let {
                    zed.rainxch.core.domain.model.installation.PresetKey.fromName(it)
                }
                if (key != null) {
                    zed.rainxch.core.domain.model.installation.InstallerAttribution.Preset(key)
                } else {
                    zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault
                }
            }
            "custom" -> {
                val name = parts.getOrNull(1).orEmpty()
                if (name.isNotBlank()) {
                    zed.rainxch.core.domain.model.installation.InstallerAttribution.Custom(name)
                } else {
                    zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault
                }
            }
            else -> zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault
        }
    }

    private fun encodeInstallerAttribution(
        attribution: zed.rainxch.core.domain.model.installation.InstallerAttribution,
    ): String = when (attribution) {
        zed.rainxch.core.domain.model.installation.InstallerAttribution.SystemDefault -> ""
        is zed.rainxch.core.domain.model.installation.InstallerAttribution.Preset -> "preset:${attribution.key.name}"
        is zed.rainxch.core.domain.model.installation.InstallerAttribution.Custom -> "custom:${attribution.packageName.trim()}"
    }

    override fun getAutoUpdateEnabled(): Flow<Boolean> = gatedGetFlow(K_AUTO_UPDATE, false)
    override suspend fun setAutoUpdateEnabled(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_AUTO_UPDATE, enabled) }

    override fun getUpdateCheckEnabled(): Flow<Boolean> = gatedGetFlow(K_UPDATE_CHECK_ENABLED, true)
    override suspend fun setUpdateCheckEnabled(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_UPDATE_CHECK_ENABLED, enabled) }

    override fun getUpdateCheckInterval(): Flow<Long> = gatedGetFlow(K_UPDATE_CHECK_INTERVAL, DEFAULT_UPDATE_CHECK_INTERVAL_HOURS)
    override suspend fun setUpdateCheckInterval(hours: Long) { migrationDeferred.await(); ksafe.safePut(K_UPDATE_CHECK_INTERVAL, hours) }

    override fun getIncludePreReleases(): Flow<Boolean> = gatedGetFlow(K_INCLUDE_PRE_RELEASES, false)
    override suspend fun setIncludePreReleases(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_INCLUDE_PRE_RELEASES, enabled) }

    override fun getHideSeenEnabled(): Flow<Boolean> = gatedGetFlow(K_HIDE_SEEN_ENABLED, false)
    override suspend fun setHideSeenEnabled(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_HIDE_SEEN_ENABLED, enabled) }

    override fun getDiscoveryPlatforms(): Flow<Set<DiscoveryPlatform>> =
        gatedGetFlow<List<String>>(K_DISCOVERY_PLATFORMS, emptyList()).map { stored ->
            stored.mapNotNull { name ->
                DiscoveryPlatform.entries.find { it.name == name && it != DiscoveryPlatform.All }
            }.toSet()
        }

    override suspend fun setDiscoveryPlatforms(platforms: Set<DiscoveryPlatform>) {
        migrationDeferred.await()
        ksafe.safePut(
            K_DISCOVERY_PLATFORMS,
            platforms.filter { it != DiscoveryPlatform.All }.map { it.name },
        )
    }

    override fun getScrollbarEnabled(): Flow<Boolean> = gatedGetFlow(K_SCROLLBAR_ENABLED, false)
    override suspend fun setScrollbarEnabled(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_SCROLLBAR_ENABLED, enabled) }

    override fun getContentWidth(): Flow<ContentWidth> =
        gatedGetFlow(K_CONTENT_WIDTH, "").map { ContentWidth.fromName(it.ifEmpty { null }) }

    override suspend fun setContentWidth(width: ContentWidth) {
        migrationDeferred.await()
        ksafe.safePut(K_CONTENT_WIDTH, width.name)
    }

    override fun getTranslationProvider(): Flow<TranslationProvider> =
        gatedGetFlow(K_TRANSLATION_PROVIDER, "").map { TranslationProvider.fromName(it.ifEmpty { null }) }

    override suspend fun setTranslationProvider(provider: TranslationProvider) {
        migrationDeferred.await()
        ksafe.safePut(K_TRANSLATION_PROVIDER, provider.name)
    }

    override fun getYoudaoAppKey(): Flow<String> = gatedGetFlow(K_YOUDAO_APP_KEY, "")
    override suspend fun setYoudaoAppKey(appKey: String) {
        migrationDeferred.await()
        val trimmed = appKey.trim()
        if (trimmed.isEmpty()) ksafe.safeDelete(K_YOUDAO_APP_KEY) else ksafe.safePut(K_YOUDAO_APP_KEY, trimmed)
    }

    override fun getYoudaoAppSecret(): Flow<String> = gatedGetFlow(K_YOUDAO_APP_SECRET, "")
    override suspend fun setYoudaoAppSecret(appSecret: String) {
        migrationDeferred.await()
        val trimmed = appSecret.trim()
        if (trimmed.isEmpty()) ksafe.safeDelete(K_YOUDAO_APP_SECRET) else ksafe.safePut(K_YOUDAO_APP_SECRET, trimmed)
    }

    override fun getLibreTranslateBaseUrl(): Flow<String> = gatedGetFlow(K_LIBRE_BASE_URL, "")
    override suspend fun setLibreTranslateBaseUrl(url: String) {
        migrationDeferred.await()
        val trimmed = url.trim()
        if (trimmed.isEmpty()) ksafe.safeDelete(K_LIBRE_BASE_URL) else ksafe.safePut(K_LIBRE_BASE_URL, trimmed)
    }

    override fun getLibreTranslateApiKey(): Flow<String> = gatedGetFlow(K_LIBRE_API_KEY, "")
    override suspend fun setLibreTranslateApiKey(apiKey: String) {
        migrationDeferred.await()
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) ksafe.safeDelete(K_LIBRE_API_KEY) else ksafe.safePut(K_LIBRE_API_KEY, trimmed)
    }

    override fun getDeeplAuthKey(): Flow<String> = gatedGetFlow(K_DEEPL_AUTH_KEY, "")
    override suspend fun setDeeplAuthKey(authKey: String) {
        migrationDeferred.await()
        val trimmed = authKey.trim()
        if (trimmed.isEmpty()) ksafe.safeDelete(K_DEEPL_AUTH_KEY) else ksafe.safePut(K_DEEPL_AUTH_KEY, trimmed)
    }

    override fun getMicrosoftTranslatorKey(): Flow<String> = gatedGetFlow(K_MS_KEY, "")
    override suspend fun setMicrosoftTranslatorKey(key: String) {
        migrationDeferred.await()
        val trimmed = key.trim()
        if (trimmed.isEmpty()) ksafe.safeDelete(K_MS_KEY) else ksafe.safePut(K_MS_KEY, trimmed)
    }

    override fun getMicrosoftTranslatorRegion(): Flow<String> = gatedGetFlow(K_MS_REGION, "")
    override suspend fun setMicrosoftTranslatorRegion(region: String) {
        migrationDeferred.await()
        val trimmed = region.trim()
        if (trimmed.isEmpty()) ksafe.safeDelete(K_MS_REGION) else ksafe.safePut(K_MS_REGION, trimmed)
    }

    override fun getAppLanguage(): Flow<String?> =
        gatedGetFlow<String?>(K_APP_LANGUAGE, null).map { raw ->
            raw?.trim()?.takeIf { it.isNotEmpty() && AppLanguages.containsTag(it) }
        }

    override suspend fun setAppLanguage(tag: String?) {
        migrationDeferred.await()
        val normalized = tag?.trim().orEmpty()
        if (normalized.isEmpty() || !AppLanguages.containsTag(normalized)) {
            ksafe.safeDelete(K_APP_LANGUAGE)
        } else {
            ksafe.safePut(K_APP_LANGUAGE, normalized)
        }
    }

    override fun getAutoTranslateEnabled(): Flow<Boolean> =
        gatedGetFlow(K_AUTO_TRANSLATE_ENABLED, false)

    override suspend fun setAutoTranslateEnabled(enabled: Boolean) {
        migrationDeferred.await()
        ksafe.put(K_AUTO_TRANSLATE_ENABLED, enabled)
    }

    override fun getAutoTranslateTargetLang(): Flow<String?> =
        gatedGetFlow<String?>(K_AUTO_TRANSLATE_TARGET, null).map { raw ->
            raw?.trim()?.takeIf { it.isNotEmpty() }
        }

    override suspend fun setAutoTranslateTargetLang(tag: String?) {
        migrationDeferred.await()
        val normalized = tag?.trim().orEmpty()
        if (normalized.isEmpty()) {
            ksafe.delete(K_AUTO_TRANSLATE_TARGET)
        } else {
            ksafe.put(K_AUTO_TRANSLATE_TARGET, normalized)
        }
    }

    override fun getExternalImportEnabled(): Flow<Boolean> = gatedGetFlow(K_EXTERNAL_IMPORT_ENABLED, true)
    override suspend fun setExternalImportEnabled(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_EXTERNAL_IMPORT_ENABLED, enabled) }

    override fun getExternalMatchSearchEnabled(): Flow<Boolean> = gatedGetFlow(K_EXTERNAL_MATCH_SEARCH_ENABLED, true)
    override suspend fun setExternalMatchSearchEnabled(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_EXTERNAL_MATCH_SEARCH_ENABLED, enabled) }

    override fun getExternalImportBannerDismissedAtCount(): Flow<Int> = gatedGetFlow(K_EXTERNAL_IMPORT_BANNER_DISMISSED_AT, 0)
    override suspend fun setExternalImportBannerDismissedAtCount(count: Int) { migrationDeferred.await(); ksafe.safePut(K_EXTERNAL_IMPORT_BANNER_DISMISSED_AT, count) }

    override fun getKaoBannerDismissed(): Flow<Boolean> = gatedGetFlow(K_KAO_BANNER_DISMISSED, false)
    override suspend fun setKaoBannerDismissed(dismissed: Boolean) { migrationDeferred.await(); ksafe.safePut(K_KAO_BANNER_DISMISSED, dismissed) }

    override fun getApkInspectCoachmarkShown(): Flow<Boolean> = gatedGetFlow(K_APK_INSPECT_COACHMARK_SHOWN, false)
    override suspend fun setApkInspectCoachmarkShown(shown: Boolean) { migrationDeferred.await(); ksafe.safePut(K_APK_INSPECT_COACHMARK_SHOWN, shown) }

    override fun getChannelChipCoachmarkShown(): Flow<Boolean> = gatedGetFlow(K_CHANNEL_CHIP_COACHMARK_SHOWN, false)
    override suspend fun setChannelChipCoachmarkShown(shown: Boolean) { migrationDeferred.await(); ksafe.safePut(K_CHANNEL_CHIP_COACHMARK_SHOWN, shown) }

    override fun getShowAllPlatforms(): Flow<Boolean> = gatedGetFlow(K_SHOW_ALL_PLATFORMS, false)
    override suspend fun setShowAllPlatforms(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_SHOW_ALL_PLATFORMS, enabled) }

    override fun getRepoGridLayout(): Flow<Boolean> = gatedGetFlow(K_REPO_GRID_LAYOUT, false)
    override suspend fun setRepoGridLayout(enabled: Boolean) { migrationDeferred.await(); ksafe.safePut(K_REPO_GRID_LAYOUT, enabled) }

    override fun getBatteryOptimizationPromptDismissed(): Flow<Boolean> = gatedGetFlow(K_BATTERY_OPT_PROMPT_DISMISSED, false)
    override suspend fun setBatteryOptimizationPromptDismissed(dismissed: Boolean) { migrationDeferred.await(); ksafe.safePut(K_BATTERY_OPT_PROMPT_DISMISSED, dismissed) }

    override fun getLastSeenWhatsNewVersionCode(): Flow<Int?> =
        gatedGetFlow<Int?>(K_LAST_SEEN_WHATS_NEW_VERSION_CODE, null)

    override suspend fun setLastSeenWhatsNewVersionCode(versionCode: Int) {
        migrationDeferred.await()
        rmwLock.withLock {
            val current = ksafe.safeGet<Int?>(K_LAST_SEEN_WHATS_NEW_VERSION_CODE, null) ?: Int.MIN_VALUE
            if (versionCode > current) {
                ksafe.safePut(K_LAST_SEEN_WHATS_NEW_VERSION_CODE, versionCode)
            }
        }
    }

    override fun getAnnouncementsDismissedIds(): Flow<Set<String>> =
        gatedGetFlow<List<String>>(K_ANNOUNCEMENTS_DISMISSED_IDS, emptyList()).map { it.toSet() }

    override suspend fun addAnnouncementDismissedId(id: String) {
        migrationDeferred.await()
        rmwLock.withLock {
            val current = ksafe.safeGet<List<String>>(K_ANNOUNCEMENTS_DISMISSED_IDS, emptyList()).toSet()
            ksafe.safePut(K_ANNOUNCEMENTS_DISMISSED_IDS, (current + id).toList())
        }
    }

    override fun getAnnouncementsAcknowledgedIds(): Flow<Set<String>> =
        gatedGetFlow<List<String>>(K_ANNOUNCEMENTS_ACKNOWLEDGED_IDS, emptyList()).map { it.toSet() }

    override suspend fun addAnnouncementAcknowledgedId(id: String) {
        migrationDeferred.await()
        rmwLock.withLock {
            val current = ksafe.safeGet<List<String>>(K_ANNOUNCEMENTS_ACKNOWLEDGED_IDS, emptyList()).toSet()
            ksafe.safePut(K_ANNOUNCEMENTS_ACKNOWLEDGED_IDS, (current + id).toList())
        }
    }

    override fun getAnnouncementsMutedCategories(): Flow<Set<AnnouncementCategory>> =
        gatedGetFlow<List<String>>(K_ANNOUNCEMENTS_MUTED_CATEGORIES, emptyList()).map { stored ->
            stored.mapNotNull { name ->
                runCatching { AnnouncementCategory.valueOf(name) }.getOrNull()
            }.toSet()
        }

    override suspend fun setAnnouncementCategoryMuted(category: AnnouncementCategory, muted: Boolean) {
        migrationDeferred.await()
        rmwLock.withLock {
            val current = ksafe.safeGet<List<String>>(K_ANNOUNCEMENTS_MUTED_CATEGORIES, emptyList()).toSet()
            val updated = if (muted) current + category.name else current - category.name
            ksafe.safePut(K_ANNOUNCEMENTS_MUTED_CATEGORIES, updated.toList())
        }
    }

    override fun getAnnouncementsLastFetchedAt(): Flow<Long> = gatedGetFlow(K_ANNOUNCEMENTS_LAST_FETCHED_AT, 0L)
    override suspend fun setAnnouncementsLastFetchedAt(epochMillis: Long) { migrationDeferred.await(); ksafe.safePut(K_ANNOUNCEMENTS_LAST_FETCHED_AT, epochMillis) }

    override fun getAppsSortRule(): Flow<String?> = gatedGetFlow<String?>(K_APPS_SORT_RULE, null)
    override suspend fun setAppsSortRule(name: String) { migrationDeferred.await(); ksafe.safePut(K_APPS_SORT_RULE, name) }

    override fun getStarredSortRule(): Flow<String?> = gatedGetFlow<String?>(K_STARRED_SORT_RULE, null)
    override suspend fun setStarredSortRule(name: String) { migrationDeferred.await(); ksafe.safePut(K_STARRED_SORT_RULE, name) }

    override fun getFavouritesSortRule(): Flow<String?> = gatedGetFlow<String?>(K_FAVOURITES_SORT_RULE, null)
    override suspend fun setFavouritesSortRule(name: String) { migrationDeferred.await(); ksafe.safePut(K_FAVOURITES_SORT_RULE, name) }

    private fun legacyEntries(): List<MigrationEntry> = listOf(
        MigrationEntry(stringPreferencesKey("app_theme"), K_THEME),
        MigrationEntry(booleanPreferencesKey("amoled_theme"), K_AMOLED),
        MigrationEntry(booleanPreferencesKey("is_dark_theme"), K_IS_DARK),
        MigrationEntry(stringPreferencesKey("font_theme"), K_FONT),
        MigrationEntry(stringSetPreferencesKey("discovery_platforms"), K_DISCOVERY_PLATFORMS),
        MigrationEntry(booleanPreferencesKey("auto_detect_clipboard_links"), K_AUTO_DETECT_CLIPBOARD),
        MigrationEntry(stringPreferencesKey("installer_type"), K_INSTALLER_TYPE),
        MigrationEntry(stringPreferencesKey("installer_attribution"), K_INSTALLER_ATTRIBUTION),
        MigrationEntry(booleanPreferencesKey("auto_update_enabled"), K_AUTO_UPDATE),
        MigrationEntry(booleanPreferencesKey("update_check_enabled"), K_UPDATE_CHECK_ENABLED),
        MigrationEntry(longPreferencesKey("update_check_interval_hours"), K_UPDATE_CHECK_INTERVAL),
        MigrationEntry(booleanPreferencesKey("include_pre_releases"), K_INCLUDE_PRE_RELEASES),
        MigrationEntry(booleanPreferencesKey("hide_seen_enabled"), K_HIDE_SEEN_ENABLED),
        MigrationEntry(booleanPreferencesKey("scrollbar_enabled"), K_SCROLLBAR_ENABLED),
        MigrationEntry(stringPreferencesKey("translation_provider"), K_TRANSLATION_PROVIDER),
        MigrationEntry(stringPreferencesKey("youdao_app_key"), K_YOUDAO_APP_KEY),
        MigrationEntry(stringPreferencesKey("youdao_app_secret"), K_YOUDAO_APP_SECRET),
        MigrationEntry(stringPreferencesKey("app_language"), K_APP_LANGUAGE),
        MigrationEntry(booleanPreferencesKey("auto_translate_enabled"), K_AUTO_TRANSLATE_ENABLED),
        MigrationEntry(stringPreferencesKey("auto_translate_target_lang"), K_AUTO_TRANSLATE_TARGET),
        MigrationEntry(booleanPreferencesKey("external_import_enabled"), K_EXTERNAL_IMPORT_ENABLED),
        MigrationEntry(booleanPreferencesKey("external_match_search_enabled"), K_EXTERNAL_MATCH_SEARCH_ENABLED),
        MigrationEntry(intPreferencesKey("external_import_banner_dismissed_at"), K_EXTERNAL_IMPORT_BANNER_DISMISSED_AT),
        MigrationEntry(booleanPreferencesKey("kao_banner_dismissed"), K_KAO_BANNER_DISMISSED),
        MigrationEntry(booleanPreferencesKey("apk_inspect_coachmark_shown"), K_APK_INSPECT_COACHMARK_SHOWN),
        MigrationEntry(booleanPreferencesKey("channel_chip_coachmark_shown"), K_CHANNEL_CHIP_COACHMARK_SHOWN),
        MigrationEntry(booleanPreferencesKey("show_all_platforms"), K_SHOW_ALL_PLATFORMS),
        MigrationEntry(booleanPreferencesKey("battery_opt_prompt_dismissed"), K_BATTERY_OPT_PROMPT_DISMISSED),
        MigrationEntry(intPreferencesKey("last_seen_whats_new_version_code"), K_LAST_SEEN_WHATS_NEW_VERSION_CODE),
        MigrationEntry(stringSetPreferencesKey("announcements_dismissed_ids"), K_ANNOUNCEMENTS_DISMISSED_IDS),
        MigrationEntry(stringSetPreferencesKey("announcements_acknowledged_ids"), K_ANNOUNCEMENTS_ACKNOWLEDGED_IDS),
        MigrationEntry(stringSetPreferencesKey("announcements_muted_categories"), K_ANNOUNCEMENTS_MUTED_CATEGORIES),
        MigrationEntry(longPreferencesKey("announcements_last_fetched_at"), K_ANNOUNCEMENTS_LAST_FETCHED_AT),
        MigrationEntry(stringPreferencesKey("apps_sort_rule"), K_APPS_SORT_RULE),
        MigrationEntry(stringPreferencesKey("starred_sort_rule"), K_STARRED_SORT_RULE),
        MigrationEntry(stringPreferencesKey("favourites_sort_rule"), K_FAVOURITES_SORT_RULE),
        MigrationEntry(stringSetPreferencesKey("custom_forge_hosts"), K_CUSTOM_FORGE_HOSTS),
    )

    override fun getCustomForgeHosts(): Flow<Set<String>> =
        gatedGetFlow<List<String>>(K_CUSTOM_FORGE_HOSTS, emptyList()).map { stored ->
            stored.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }.toSet()
        }

    override suspend fun addCustomForgeHost(host: String) {
        migrationDeferred.await()
        val normalized = host.trim().lowercase()
        if (normalized.isEmpty()) return
        rmwLock.withLock {
            val current = ksafe.safeGet<List<String>>(K_CUSTOM_FORGE_HOSTS, emptyList()).toSet()
            ksafe.safePut(K_CUSTOM_FORGE_HOSTS, (current + normalized).toList())
        }
    }

    override suspend fun removeCustomForgeHost(host: String) {
        migrationDeferred.await()
        val normalized = host.trim().lowercase()
        if (normalized.isEmpty()) return
        rmwLock.withLock {
            val current = ksafe.safeGet<List<String>>(K_CUSTOM_FORGE_HOSTS, emptyList()).toSet()
            if (normalized !in current) return@withLock
            ksafe.safePut(K_CUSTOM_FORGE_HOSTS, (current - normalized).toList())
        }
    }

    companion object {
        private const val DEFAULT_UPDATE_CHECK_INTERVAL_HOURS = 12L
        private const val MIGRATION_MARKER = "__migrated_from_datastore_v1__"

        private const val K_THEME = "app_theme"
        private const val K_AMOLED = "amoled_theme"
        private const val K_IS_DARK = "is_dark_theme"
        private const val K_FONT = "font_theme"
        private const val K_PERSONALITY = "app_personality"
        private const val K_ACCENT = "app_accent"
        private const val K_DISCOVERY_PLATFORMS = "discovery_platforms"
        private const val K_AUTO_DETECT_CLIPBOARD = "auto_detect_clipboard_links"
        private const val K_INSTALLER_TYPE = "installer_type"
        private const val K_INSTALLER_ATTRIBUTION = "installer_attribution"
        private const val K_AUTO_UPDATE = "auto_update_enabled"
        private const val K_UPDATE_CHECK_ENABLED = "update_check_enabled"
        private const val K_UPDATE_CHECK_INTERVAL = "update_check_interval_hours"
        private const val K_INCLUDE_PRE_RELEASES = "include_pre_releases"
        private const val K_HIDE_SEEN_ENABLED = "hide_seen_enabled"
        private const val K_SCROLLBAR_ENABLED = "scrollbar_enabled"
        private const val K_TRANSLATION_PROVIDER = "translation_provider"
        private const val K_YOUDAO_APP_KEY = "youdao_app_key"
        private const val K_YOUDAO_APP_SECRET = "youdao_app_secret"
        private const val K_LIBRE_BASE_URL = "libre_translate_base_url"
        private const val K_LIBRE_API_KEY = "libre_translate_api_key"
        private const val K_DEEPL_AUTH_KEY = "deepl_auth_key"
        private const val K_MS_KEY = "microsoft_translator_key"
        private const val K_MS_REGION = "microsoft_translator_region"
        private const val K_APP_LANGUAGE = "app_language"
        private const val K_AUTO_TRANSLATE_ENABLED = "auto_translate_enabled"
        private const val K_AUTO_TRANSLATE_TARGET = "auto_translate_target_lang"
        private const val K_EXTERNAL_IMPORT_ENABLED = "external_import_enabled"
        private const val K_EXTERNAL_MATCH_SEARCH_ENABLED = "external_match_search_enabled"
        private const val K_EXTERNAL_IMPORT_BANNER_DISMISSED_AT = "external_import_banner_dismissed_at"
        private const val K_KAO_BANNER_DISMISSED = "kao_banner_dismissed"
        private const val K_APK_INSPECT_COACHMARK_SHOWN = "apk_inspect_coachmark_shown"
        private const val K_CHANNEL_CHIP_COACHMARK_SHOWN = "channel_chip_coachmark_shown"
        private const val K_SHOW_ALL_PLATFORMS = "show_all_platforms"
        private const val K_REPO_GRID_LAYOUT = "repo_grid_layout"
        private const val K_BATTERY_OPT_PROMPT_DISMISSED = "battery_opt_prompt_dismissed"
        private const val K_LAST_SEEN_WHATS_NEW_VERSION_CODE = "last_seen_whats_new_version_code"
        private const val K_MANGA_PAPER = "manga_paper"
        private const val K_ANNOUNCEMENTS_DISMISSED_IDS = "announcements_dismissed_ids"
        private const val K_ANNOUNCEMENTS_ACKNOWLEDGED_IDS = "announcements_acknowledged_ids"
        private const val K_ANNOUNCEMENTS_MUTED_CATEGORIES = "announcements_muted_categories"
        private const val K_ANNOUNCEMENTS_LAST_FETCHED_AT = "announcements_last_fetched_at"
        private const val K_APPS_SORT_RULE = "apps_sort_rule"
        private const val K_STARRED_SORT_RULE = "starred_sort_rule"
        private const val K_FAVOURITES_SORT_RULE = "favourites_sort_rule"
        private const val K_CONTENT_WIDTH = "content_width"
        private const val K_CUSTOM_FORGE_HOSTS = "custom_forge_hosts"
    }
}
