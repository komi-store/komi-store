# Home Feature

Main discovery — Trending, Hot Releases, Most Popular. Infinite-scroll pagination. Integrates installed-app / favourite / starred status badges.

## Structure

```
feature/home/
├── domain/    # HomeRepository + HomeCategory (TRENDING / HOT_RELEASE / MOST_POPULAR), TopicCategory
├── data/      # HomeRepositoryImpl, CachedRepositoriesDataSource (per-category 7-day TTL), dto, mappers, di
└── presentation/
    ├── HomeViewModel / State / Action / Event / Root
    ├── components/HomeFilterChips
    ├── locals/LocalHomeTopBarLiquid
    └── utils/HomeCategoryMapper
```

## Key interface

```kotlin
interface HomeRepository {
    fun getTrendingRepositories(page: Int): Flow<PaginatedDiscoveryRepositories>
    fun getHotReleaseRepositories(page: Int): Flow<PaginatedDiscoveryRepositories>
    fun getMostPopular(page: Int): Flow<PaginatedDiscoveryRepositories>
}
```

## Navigation

`GithubStoreGraph.ChartsScreen` (bottom-nav label "Top Charts"). The `ExploreScreen` route renders the feed feature, not this one.

## VM injects

`HomeRepository`, `InstalledAppsRepository`, `Platform`, `SyncInstalledAppsUseCase`, `FavouritesRepository`, `StarredRepository`, `GitHubStoreLogger`, `ShareManager`, `TweaksRepository`, `SeenReposRepository`, `HiddenReposRepository`, `ProfileRepository`.

## Notes

- `Semaphore` in `HomeRepositoryImpl` for concurrent request control. 7-day per-category cache. Pagination via `nextPageIndex`, dedupe by `fullName`.
- `HomeRoot.visibleRepos` derives display list — filters by `hiddenRepoIds` (E11) and `seenRepoIds` when `isHideSeenEnabled`.
- Apps section in bottom nav: `Platform.ANDROID` only.
- Long-press on `RepositoryCard` opens `RepositoryActionsBottomSheet` (Share / Open on GitHub / Mark seen / Hide).
- `DiscoveryRepositoryUi.isCurrentUserOwner` flipped by `observeCurrentUser` (E20).
- State uses `onStart` + `stateIn(WhileSubscribed)`.
- Per-platform release date: a list filtered to one platform shows that platform's newest build date (`BackendRepoResponse.platformReleases` via `releaseDateFor(platform)` in `toCachedGithubRepoSummary(platform)`); `All` keeps the newest release overall. Feed and Search do the same through `toSummary(platform)`. Matters for repos that release desktop and mobile separately.
