package zed.rainxch.home.presentation

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.home.presentation.model.ChartTab
import zed.rainxch.home.presentation.model.HomeRepoCardUi

@Stable
data class HomeState(
    val selectedChart: ChartTab = ChartTab.Trending,
    val repos: ImmutableList<HomeRepoCardUi> = persistentListOf(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val selectedPlatform: DiscoveryPlatform = DiscoveryPlatform.All,
    val isPlatformPopupVisible: Boolean = false,
    val actionSheetCard: HomeRepoCardUi? = null,
    val isGridLayout: Boolean = false,
)
