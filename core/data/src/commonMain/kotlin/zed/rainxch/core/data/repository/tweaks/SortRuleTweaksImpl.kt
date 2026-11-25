package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.repository.tweaks.SortRuleTweaks

internal class SortRuleTweaksImpl(
    private val store: TweakStore,
) : SortRuleTweaks {
    override fun getAppsSortRule(): Flow<String?> = store.gatedGetFlow<String?>(K_APPS_SORT_RULE, null)

    override suspend fun setAppsSortRule(name: String) {
        store.awaitMigration()
        store.ksafe.safePut(K_APPS_SORT_RULE, name)
    }

    override fun getStarredSortRule(): Flow<String?> = store.gatedGetFlow<String?>(K_STARRED_SORT_RULE, null)

    override suspend fun setStarredSortRule(name: String) {
        store.awaitMigration()
        store.ksafe.safePut(K_STARRED_SORT_RULE, name)
    }

    override fun getFavouritesSortRule(): Flow<String?> = store.gatedGetFlow<String?>(K_FAVOURITES_SORT_RULE, null)

    override suspend fun setFavouritesSortRule(name: String) {
        store.awaitMigration()
        store.ksafe.safePut(K_FAVOURITES_SORT_RULE, name)
    }
}
