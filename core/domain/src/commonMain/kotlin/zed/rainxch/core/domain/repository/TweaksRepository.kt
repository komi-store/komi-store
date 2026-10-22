package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.AppTheme
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.domain.model.appearance.FontTheme
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.settings.TranslationProvider

interface TweaksRepository {
    fun getThemeColor(): Flow<AppTheme>

    suspend fun setThemeColor(theme: AppTheme)

    fun getIsDarkTheme(): Flow<Boolean?>

    suspend fun setDarkTheme(isDarkTheme: Boolean?)

    fun getAmoledTheme(): Flow<Boolean>

    suspend fun setAmoledTheme(enabled: Boolean)

    fun getMangaPaper(): Flow<MangaPaperId>

    suspend fun setMangaPaper(paper: MangaPaperId)

    fun getFontTheme(): Flow<FontTheme>

    suspend fun setFontTheme(fontTheme: FontTheme)

    fun getPersonality(): Flow<AppPersonality>

    suspend fun setPersonality(personality: AppPersonality)

    fun getAccentId(): Flow<AccentId>

    suspend fun setAccentId(accentId: AccentId)

    fun getAutoDetectClipboardLinks(): Flow<Boolean>

    suspend fun setAutoDetectClipboardLinks(enabled: Boolean)

    fun getInstallerType(): Flow<InstallerType>

    suspend fun setInstallerType(type: InstallerType)

    fun getExternalInstallerPackage(): Flow<String>

    suspend fun setExternalInstallerPackage(packageName: String)

    fun getInstallerAttribution(): Flow<zed.rainxch.core.domain.model.installation.InstallerAttribution>

    suspend fun setInstallerAttribution(attribution: zed.rainxch.core.domain.model.installation.InstallerAttribution)

    fun getAutoUpdateEnabled(): Flow<Boolean>

    suspend fun setAutoUpdateEnabled(enabled: Boolean)

    fun getUpdateCheckEnabled(): Flow<Boolean>

    suspend fun setUpdateCheckEnabled(enabled: Boolean)

    fun getUpdateCheckInterval(): Flow<Long>

    suspend fun setUpdateCheckInterval(hours: Long)

    fun getIncludePreReleases(): Flow<Boolean>

    suspend fun setIncludePreReleases(enabled: Boolean)

    fun getHideSeenEnabled(): Flow<Boolean>

    suspend fun setHideSeenEnabled(enabled: Boolean)

    fun getDiscoveryPlatforms(): Flow<Set<DiscoveryPlatform>>

    suspend fun setDiscoveryPlatforms(platforms: Set<DiscoveryPlatform>)

    fun getScrollbarEnabled(): Flow<Boolean>

    suspend fun setScrollbarEnabled(enabled: Boolean)

    fun getDisplayModeId(): Flow<Int>

    suspend fun setDisplayModeId(modeId: Int)

    fun getContentWidth(): Flow<ContentWidth>

    suspend fun setContentWidth(width: ContentWidth)

    fun getTranslationProvider(): Flow<TranslationProvider>

    suspend fun setTranslationProvider(provider: TranslationProvider)

    fun getYoudaoAppKey(): Flow<String>

    suspend fun setYoudaoAppKey(appKey: String)

    fun getYoudaoAppSecret(): Flow<String>

    suspend fun setYoudaoAppSecret(appSecret: String)

    fun getLibreTranslateBaseUrl(): Flow<String>

    suspend fun setLibreTranslateBaseUrl(url: String)

    fun getLibreTranslateApiKey(): Flow<String>

    suspend fun setLibreTranslateApiKey(apiKey: String)

    fun getDeeplAuthKey(): Flow<String>

    suspend fun setDeeplAuthKey(authKey: String)

    fun getMicrosoftTranslatorKey(): Flow<String>

    suspend fun setMicrosoftTranslatorKey(key: String)

    fun getMicrosoftTranslatorRegion(): Flow<String>

    suspend fun setMicrosoftTranslatorRegion(region: String)

    fun getAppLanguage(): Flow<String?>

    suspend fun setAppLanguage(tag: String?)

    fun getAutoTranslateEnabled(): Flow<Boolean>

    suspend fun setAutoTranslateEnabled(enabled: Boolean)

    fun getAutoTranslateTargetLang(): Flow<String?>

    suspend fun setAutoTranslateTargetLang(tag: String?)

    fun getExternalImportEnabled(): Flow<Boolean>

    suspend fun setExternalImportEnabled(enabled: Boolean)

    fun getExternalMatchSearchEnabled(): Flow<Boolean>

    suspend fun setExternalMatchSearchEnabled(enabled: Boolean)

    fun getExternalImportBannerDismissedAtCount(): Flow<Int>

    suspend fun setExternalImportBannerDismissedAtCount(count: Int)

    fun getApkInspectCoachmarkShown(): Flow<Boolean>

    suspend fun setApkInspectCoachmarkShown(shown: Boolean)

    fun getChannelChipCoachmarkShown(): Flow<Boolean>

    suspend fun setChannelChipCoachmarkShown(shown: Boolean)

    fun getShowAllPlatforms(): Flow<Boolean>

    suspend fun setShowAllPlatforms(enabled: Boolean)

    fun getRepoGridLayout(): Flow<Boolean>

    suspend fun setRepoGridLayout(enabled: Boolean)

    fun getBatteryOptimizationPromptDismissed(): Flow<Boolean>

    suspend fun setBatteryOptimizationPromptDismissed(dismissed: Boolean)

    fun getLastSeenWhatsNewVersionCode(): Flow<Int?>

    suspend fun setLastSeenWhatsNewVersionCode(versionCode: Int)

    fun getAnnouncementsDismissedIds(): Flow<Set<String>>

    suspend fun addAnnouncementDismissedId(id: String)

    fun getAnnouncementsAcknowledgedIds(): Flow<Set<String>>

    suspend fun addAnnouncementAcknowledgedId(id: String)

    fun getAnnouncementsMutedCategories(): Flow<Set<AnnouncementCategory>>

    suspend fun setAnnouncementCategoryMuted(
        category: AnnouncementCategory,
        muted: Boolean,
    )

    fun getAnnouncementsLastFetchedAt(): Flow<Long>

    suspend fun setAnnouncementsLastFetchedAt(epochMillis: Long)

    fun getAppsSortRule(): Flow<String?>

    suspend fun setAppsSortRule(name: String)

    fun getStarredSortRule(): Flow<String?>

    suspend fun setStarredSortRule(name: String)

    fun getFavouritesSortRule(): Flow<String?>

    suspend fun setFavouritesSortRule(name: String)

    fun getCustomForgeHosts(): Flow<Set<String>>

    suspend fun addCustomForgeHost(host: String)

    suspend fun removeCustomForgeHost(host: String)
}

// Matches WindowManager.LayoutParams.preferredDisplayModeId: 0 leaves the system in charge.
const val SYSTEM_DEFAULT_DISPLAY_MODE_ID = 0
