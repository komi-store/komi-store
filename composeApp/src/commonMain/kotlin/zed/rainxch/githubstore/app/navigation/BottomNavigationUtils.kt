package zed.rainxch.githubstore.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Person2
import androidx.compose.material.icons.outlined.Search
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.bottom_nav_apps_title
import zed.rainxch.githubstore.core.presentation.res.bottom_nav_explore_title
import zed.rainxch.githubstore.core.presentation.res.bottom_nav_profile_title
import zed.rainxch.githubstore.core.presentation.res.bottom_nav_search_title
import zed.rainxch.githubstore.core.presentation.res.bottom_nav_top_charts_title

object BottomNavigationUtils {
    fun items(): List<BottomNavigationItem> =
        listOf(
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_explore_title,
                iconOutlined = Icons.Outlined.Explore,
                iconFilled = Icons.Filled.Explore,
                screen = GithubStoreGraph.ExploreScreen,
            ),
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_top_charts_title,
                iconOutlined = Icons.Outlined.Leaderboard,
                iconFilled = Icons.Filled.Leaderboard,
                screen = GithubStoreGraph.ChartsScreen,
            ),
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_search_title,
                iconOutlined = Icons.Outlined.Search,
                iconFilled = Icons.Filled.Search,
                screen = GithubStoreGraph.SearchScreen(),
            ),
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_apps_title,
                iconOutlined = Icons.Outlined.Apps,
                iconFilled = Icons.Filled.Apps,
                screen = GithubStoreGraph.AppsScreen,
            ),
            BottomNavigationItem(
                titleRes = Res.string.bottom_nav_profile_title,
                iconOutlined = Icons.Outlined.Person2,
                iconFilled = Icons.Filled.Person2,
                screen = GithubStoreGraph.ProfileGraph.ProfileScreen,
            ),
        )

    // The bottom bar leaves Search out: Classic's tab card has a search field, Manga's Explore and Top Charts a search button.
    fun allowedScreens(): List<BottomNavigationItem> = items().filterNot { it.screen is GithubStoreGraph.SearchScreen }
}
