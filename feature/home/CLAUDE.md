# Home Feature

Top Charts: Trending / Releases / Popular tabs (`ChartTab`) over backend category lists, with installed / update / favourite / starred / seen badges. Also holds the category list screen (`categorylist/`).

## Structure

```
feature/home/
├── domain/    # HomeRepository + HomeCategory (TRENDING / HOT_RELEASE / MOST_POPULAR), TopicCategory
├── data/      # HomeRepositoryImpl, CachedRepositoriesDataSource (backend + offline mirror, 1h in-memory cache), dto, mappers, di
└── presentation/
    ├── HomeViewModel / State / Action / Event / Root
    ├── components/    RepositoryActionsSheet
    ├── model/         ChartTab, HomeRepoCardUi + mapper
    └── categorylist/  CategoryListRoot / ViewModel
```

`HomeRepository` takes a set of `DiscoveryPlatform`s; an empty set means all platforms. `searchByTopic`, `getTopicRepositories` and `getRepositoryById` are implemented but have no callers.

## Navigation

`GithubStoreGraph.ChartsScreen` (bottom-nav label "Top Charts"). The `ExploreScreen` route renders the feed feature, not this one. `CategoryListScreen(category)` is registered (`HomeCategory.valueOf`, falls back to HOT_RELEASE) but nothing navigates to it.

## Notes

- Page 1 of a chart: `CachedRepositoriesDataSource` (backend category list, offline mirror JSON under `OFFLINE_MIRROR_BASE` if the backend fails) -> `CacheManager` copy (`HOME_REPOS`, 24h) -> direct GitHub `/search/repositories`, checking candidates for installers under a `Semaphore`. Later pages skip the data source. Backend / mirror lists come as one page (`hasMore = false`); only the GitHub search path paginates.
- The screen body is core's `DiscoveryFeed`, shared with Explore: the charts are its chips. Its top-bar actions are `DiscoveryPlatformButton` / `DiscoveryPlatformPicker` and, in Manga (no tab card), `DiscoverySearchButton` (`HomeAction.OnSearchClick`, which `HomeRoot` turns into `onNavigateToSearch`), hidden on Desktop, where the sidebar sets the platform. List or grid comes from Tweaks (`TweaksRepository.getRepoGridLayout`).
- Platform comes from the shared `BrowseFilterStore` (`core`, in-memory, starts at the device platform; also set by Feed and the Desktop sidebar). A change clears every chart and reloads the selected one. The VM passes one platform or none.
- Each tab keeps its own repos / page / hasMore in the VM's `ChartCache`, so switching back to a loaded tab does not refetch. Load-more dedupes by `id`.
- `HomeViewModel.filterVisible` drops hidden ids and, when hide-seen is on, seen ids.
- Long-press on a `DiscoveryRepoCard` opens `RepositoryActionsSheet` (Share / Open on GitHub / Mark seen or unseen / Hide).
- `HomeRepoCardUi.isCurrentUserOwner` set from `observeCurrentUser` (login match on `UserSessionRepository.getUser()`).
- `categorylist/`: one `HomeCategory` across all platforms, no installed / favourite / starred badges.
- Per-platform release date: a list filtered to one platform shows that platform's newest build date (`BackendRepoResponse.platformReleases` via `releaseDateFor(platform)` in `toCachedGithubRepoSummary(platform)`); `All` keeps the newest release overall. Feed and Search do the same through `toSummary(platform)`. Matters for repos that release desktop and mobile separately.
