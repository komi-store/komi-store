package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.domain.repository.tweaks.AnnouncementTweaks

internal class AnnouncementTweaksImpl(
    private val store: TweakStore,
) : AnnouncementTweaks {
    override fun getAnnouncementsDismissedIds(): Flow<Set<String>> =
        store.gatedGetFlow<List<String>>(K_ANNOUNCEMENTS_DISMISSED_IDS, emptyList()).map { it.toSet() }

    override suspend fun addAnnouncementDismissedId(id: String) {
        store.awaitMigration()
        store.rmwLock.withLock {
            val current = store.ksafe.safeGet<List<String>>(K_ANNOUNCEMENTS_DISMISSED_IDS, emptyList()).toSet()
            store.ksafe.safePut(K_ANNOUNCEMENTS_DISMISSED_IDS, (current + id).toList())
        }
    }

    override fun getAnnouncementsAcknowledgedIds(): Flow<Set<String>> =
        store.gatedGetFlow<List<String>>(K_ANNOUNCEMENTS_ACKNOWLEDGED_IDS, emptyList()).map { it.toSet() }

    override suspend fun addAnnouncementAcknowledgedId(id: String) {
        store.awaitMigration()
        store.rmwLock.withLock {
            val current = store.ksafe.safeGet<List<String>>(K_ANNOUNCEMENTS_ACKNOWLEDGED_IDS, emptyList()).toSet()
            store.ksafe.safePut(K_ANNOUNCEMENTS_ACKNOWLEDGED_IDS, (current + id).toList())
        }
    }

    override fun getAnnouncementsMutedCategories(): Flow<Set<AnnouncementCategory>> =
        store.gatedGetFlow<List<String>>(K_ANNOUNCEMENTS_MUTED_CATEGORIES, emptyList()).map { stored ->
            stored
                .mapNotNull { name ->
                    runCatching { AnnouncementCategory.valueOf(name) }.getOrNull()
                }.toSet()
        }

    override suspend fun setAnnouncementCategoryMuted(
        category: AnnouncementCategory,
        muted: Boolean,
    ) {
        store.awaitMigration()
        store.rmwLock.withLock {
            val current = store.ksafe.safeGet<List<String>>(K_ANNOUNCEMENTS_MUTED_CATEGORIES, emptyList()).toSet()
            val updated = if (muted) current + category.name else current - category.name
            store.ksafe.safePut(K_ANNOUNCEMENTS_MUTED_CATEGORIES, updated.toList())
        }
    }

    override fun getAnnouncementsLastFetchedAt(): Flow<Long> = store.gatedGetFlow(K_ANNOUNCEMENTS_LAST_FETCHED_AT, 0L)

    override suspend fun setAnnouncementsLastFetchedAt(epochMillis: Long) {
        store.awaitMigration()
        store.ksafe.safePut(K_ANNOUNCEMENTS_LAST_FETCHED_AT, epochMillis)
    }
}
