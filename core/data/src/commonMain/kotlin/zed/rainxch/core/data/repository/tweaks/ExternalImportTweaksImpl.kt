package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.repository.tweaks.ExternalImportTweaks

internal class ExternalImportTweaksImpl(
    private val store: TweakStore,
) : ExternalImportTweaks {
    override fun getExternalImportEnabled(): Flow<Boolean> = store.gatedGetFlow(K_EXTERNAL_IMPORT_ENABLED, true)

    override suspend fun setExternalImportEnabled(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_EXTERNAL_IMPORT_ENABLED, enabled)
    }

    override fun getExternalMatchSearchEnabled(): Flow<Boolean> = store.gatedGetFlow(K_EXTERNAL_MATCH_SEARCH_ENABLED, true)

    override suspend fun setExternalMatchSearchEnabled(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_EXTERNAL_MATCH_SEARCH_ENABLED, enabled)
    }

    override fun getExternalImportBannerDismissedAtCount(): Flow<Int> = store.gatedGetFlow(K_EXTERNAL_IMPORT_BANNER_DISMISSED_AT, 0)

    override suspend fun setExternalImportBannerDismissedAtCount(count: Int) {
        store.awaitMigration()
        store.ksafe.safePut(K_EXTERNAL_IMPORT_BANNER_DISMISSED_AT, count)
    }
}
