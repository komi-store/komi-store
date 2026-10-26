package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow

interface SortRuleTweaks {
    fun getAppsSortRule(): Flow<String?>

    suspend fun setAppsSortRule(name: String)

    fun getStarredSortRule(): Flow<String?>

    suspend fun setStarredSortRule(name: String)

    fun getFavouritesSortRule(): Flow<String?>

    suspend fun setFavouritesSortRule(name: String)
}
