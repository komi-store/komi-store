package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.dto.AnnouncementsResponseDto
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.domain.getPlatform
import zed.rainxch.core.domain.model.announcement.Announcement
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.domain.model.announcement.AnnouncementsFeedSnapshot
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.AnnouncementsCacheStore
import zed.rainxch.core.domain.repository.AnnouncementsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.AppVersionInfo
import kotlin.time.Clock

class AnnouncementsRepositoryImpl(
    private val backendApiClient: BackendApiClient,
    private val tweaksRepository: TweaksRepository,
    private val cacheStore: AnnouncementsCacheStore,
    private val localizationManager: LocalizationManager,
    private val appVersionInfo: AppVersionInfo,
) : AnnouncementsRepository {
    private val logger = Logger.withTag("AnnouncementsRepository")

    private val json = Json { ignoreUnknownKeys = true }

    private val platformTag: String =
        when (getPlatform()) {
            Platform.ANDROID -> "ANDROID"
            Platform.WINDOWS, Platform.MACOS, Platform.LINUX -> "DESKTOP"
        }

    private val lastRefreshFailed = MutableStateFlow(false)

    override fun observeFeed(): Flow<AnnouncementsFeedSnapshot> {
        val persistedFlow =
            combine(
                cacheStore.getCachedPayload(),
                tweaksRepository.getAnnouncementsDismissedIds(),
                tweaksRepository.getAnnouncementsAcknowledgedIds(),
                tweaksRepository.getAnnouncementsMutedCategories(),
                tweaksRepository.getAnnouncementsLastFetchedAt(),
            ) { payload, dismissed, acknowledged, mutedCategories, lastFetched ->
                PersistedFeedState(payload, dismissed, acknowledged, mutedCategories, lastFetched)
            }
        return combine(
            persistedFlow,
            lastRefreshFailed,
            tweaksRepository.getInstallerType(),
            tweaksRepository.getAppLanguage(),
        ) { persisted, refreshFailed, installerType, _ ->
            val items = parseAndFilter(persisted.payload, installerType.name)
            AnnouncementsFeedSnapshot(
                items = items,
                dismissedIds = persisted.dismissed,
                acknowledgedIds = persisted.acknowledged,
                mutedCategories = persisted.mutedCategories,
                lastFetchedAtMillis = persisted.lastFetched,
                lastRefreshFailed = refreshFailed,
            )
        }
    }

    private data class PersistedFeedState(
        val payload: String?,
        val dismissed: Set<String>,
        val acknowledged: Set<String>,
        val mutedCategories: Set<AnnouncementCategory>,
        val lastFetched: Long,
    )

    override suspend fun refresh(): Result<Unit> {
        val result = backendApiClient.getAnnouncements()
        return result.fold(
            onSuccess = { dto ->
                try {
                    val raw = json.encodeToString(AnnouncementsResponseDto.serializer(), dto)
                    cacheStore.setCachedPayload(raw)
                    tweaksRepository.setAnnouncementsLastFetchedAt(nowMillis())
                    lastRefreshFailed.value = false
                    Result.success(Unit)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    logger.w("Announcements cache write failed: ${t.message}")
                    lastRefreshFailed.value = true
                    Result.failure(t)
                }
            },
            onFailure = { error ->
                logger.w("Announcements refresh failed: ${error.message}")
                lastRefreshFailed.value = true
                Result.failure(error)
            },
        )
    }

    override suspend fun dismiss(id: String) {
        try {
            tweaksRepository.addAnnouncementDismissedId(id)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            logger.e(t) { "Failed to persist dismissed announcement $id" }
        }
    }

    override suspend fun acknowledge(id: String) {
        try {
            tweaksRepository.addAnnouncementAcknowledgedId(id)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            logger.e(t) { "Failed to persist acknowledged announcement $id" }
        }
    }

    override suspend fun setMuted(
        category: AnnouncementCategory,
        muted: Boolean,
    ) {
        if (!category.isMutable) return
        try {
            tweaksRepository.setAnnouncementCategoryMuted(category, muted)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            logger.e(t) { "Failed to persist mute toggle for $category" }
        }
    }

    private fun parseAndFilter(
        payload: String?,
        installerTypeTag: String,
    ): List<Announcement> {
        if (payload.isNullOrBlank()) return emptyList()
        val parsed =
            runCatching {
                json.decodeFromString(AnnouncementsResponseDto.serializer(), payload)
            }.getOrElse {
                logger.w("Failed to parse cached announcements payload: ${it.message}")
                return emptyList()
            }
        val full = localizationManager.getCurrentLanguageCode()
        val primary = localizationManager.getPrimaryLanguageCode()
        val now = Clock.System.now()
        val versionCode = appVersionInfo.versionCode

        return parsed.items
            .asSequence()
            .mapNotNull { it.toDomain(fullLocale = full, primaryLocale = primary) }
            .filter { item ->
                val expired = item.expiresAt?.let { it < now } == true
                val publishedOk = item.publishedAt <= now
                val minVc = item.minVersionCode
                val maxVc = item.maxVersionCode
                val versionFloorOk = minVc == null || versionCode >= minVc
                val versionCeilingOk = maxVc == null || versionCode <= maxVc
                val platformOk = item.platforms?.contains(platformTag) ?: true
                val installerOk = item.installerTypes?.contains(installerTypeTag) ?: true
                !expired && publishedOk && versionFloorOk && versionCeilingOk && platformOk && installerOk
            }.sortedByDescending { it.publishedAt }
            .toList()
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
