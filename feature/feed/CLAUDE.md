# Feed Feature

Explore screen: the backend's repo feed, filtered by platform and category, with installed / update / favourite / starred / seen badges. Infinite scroll.

## Structure

```
feature/feed/
├── domain/   # FeedRepository, FeedPage (items, page, hasMore, rotation, fromCache, isOffline)
├── data/     # FeedRepositoryImpl + di (feedModule)
└── presentation/
    ├── FeedViewModel / State / Action / Event / Root
    ├── components/  FeedPlatformBar (unused)
    └── model/       GithubRepoSummary.toDiscoveryRepoUi
```

## Navigation

`GithubStoreGraph.ExploreScreen` (start destination, bottom-nav label "Explore"). `FeedRoot(onNavigateToDetails, onNavigateToSearch, onNavigateToProfile)`.

## Notes

- `FeedRepository.getFeed(platform, page, forceRefresh): Result<FeedPage>` calls backend `/v1/feed` (`BackendApiClient.getFeed`, 20 per page). `All` sends no platform.
- Cache: `CacheManager` key `feed:<platform|all>:<page>:<rotation>`, TTL `FEED` (3h). Read with today's UTC date as rotation, written under the response's `rotation`. Pull-to-refresh passes `forceRefresh` and skips the read.
- On backend failure: stale cache entry for the same key, then (page 1 only) the offline mirror `OFFLINE_MIRROR_BASE/cached-data/feed/<platform>.json` (android / windows / macos / linux merged for `All`), returned with `isOffline = true`, `hasMore = false`; the screen shows an offline banner.
- Platform and category live in the shared `BrowseFilterStore` (`core`, also used by Home, Search on Desktop and the Desktop sidebar). A platform change refetches; a category change only re-filters loaded items by `FeedCategory.matches(repo.topicCodes)`.
- The screen body is core's `DiscoveryFeed` (`components/discovery`), shared with Top Charts: the category chips, the grid with its loading / error / empty / offline / load-more / end states, and pull to refresh; `FeedRoot` brings the cards and its empty state with Reset. The only top-bar action is `DiscoveryPlatformButton`, which opens `DiscoveryPlatformPicker`. On Desktop the sidebar holds categories and platform, so the chips and the button are hidden there.
- `DiscoveryPlatformPicker` offers every `DiscoveryPlatform` except iOS: `/v1/feed` rejects `platform=ios` and its items carry no iOS data. The Desktop sidebar leaves iOS out too, so Feed never receives it; iOS discovery is in Search.
- List or grid comes from `TweaksRepository.getRepoGridLayout`, set in Tweaks (Look & feel).
- Long-press opens `DiscoveryRepoCard`'s built-in action sheet (Share / Open on GitHub / seen toggle / Hide), unlike Home's VM-driven `RepositoryActionsSheet`. Hidden repos, and seen ones when hide-seen is on, are filtered in `rebuild()`.
- Search is not a tab: in Manga (no tab card, `LocalTabCardSlot` null) the top bar also has `DiscoverySearchButton`, which sends `FeedAction.OnSearchClick`; `FeedRoot` opens Search.
- Unused: `FeedPlatformBar`, and `FeedAction.OnProfileClick` (handled by `FeedRoot`, never dispatched).
