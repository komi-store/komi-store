package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.repository.tweaks.UpdateTweaks

internal class UpdateTweaksImpl(
    private val store: TweakStore,
) : UpdateTweaks {
    override fun getAutoUpdateEnabled(): Flow<Boolean> = store.gatedGetFlow(K_AUTO_UPDATE, false)

    override suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_AUTO_UPDATE, enabled)
    }

    override fun getUpdateCheckEnabled(): Flow<Boolean> = store.gatedGetFlow(K_UPDATE_CHECK_ENABLED, true)

    override suspend fun setUpdateCheckEnabled(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_UPDATE_CHECK_ENABLED, enabled)
    }

    override fun getUpdateCheckInterval(): Flow<Long> = store.gatedGetFlow(K_UPDATE_CHECK_INTERVAL, DEFAULT_UPDATE_CHECK_INTERVAL_HOURS)

    override suspend fun setUpdateCheckInterval(hours: Long) {
        store.awaitMigration()
        store.ksafe.safePut(K_UPDATE_CHECK_INTERVAL, hours)
    }

    override fun getIncludePreReleases(): Flow<Boolean> = store.gatedGetFlow(K_INCLUDE_PRE_RELEASES, false)

    override suspend fun setIncludePreReleases(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_INCLUDE_PRE_RELEASES, enabled)
    }

    private companion object {
        const val DEFAULT_UPDATE_CHECK_INTERVAL_HOURS = 12L
    }
}
