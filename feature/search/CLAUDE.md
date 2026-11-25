# Search Feature

Repo search with source / platform / language / sort filters. Paginated results.

## Structure

```text
feature/search/
  domain/   # SearchRepository, SearchHistoryRepository; SearchRequest, SearchSource, ProgrammingLanguage, SortBy, SortOrder, ExploreResult
  data/     # SearchRepositoryImpl over BackendSearch, GithubSearch and InstallerReleaseCheck; SearchHistoryRepositoryImpl, dto/, di
  presentation/
    SearchViewModel / State / Action / Event / Root
    SearchDerivedState  # visibleRepos, activeFilterCount and the flags the screen branches on
    handlers/           # SearchStore and the collaborators SearchViewModel hands each SearchAction group to
    model/ + mappers/   # UI models (SearchSourceUi, SortByUi, ...) and toDomain() / toSearchRequest()
    components/         # top bar, filters, sort and language sheets, link banners, results grid, status messages, search history
```

Platform filter type is `DiscoveryPlatform` (core/domain).

`SearchSource` is `GitHub` or `Forgejo(host)`; Codeberg is `Forgejo("codeberg.org")`.

## Navigation

`GithubStoreGraph.SearchScreen(initialPlatform: String?)`. The name is parsed to `DiscoveryPlatform` and passed to the VM via `parametersOf`. No caller sets it today. On Desktop the VM follows the sidebar's `BrowseFilterStore.platform` instead (`SearchFilters.observe`).

## ViewModel

`SearchViewModel` keeps the one state, `onAction` and event channel, and hands each `SearchAction` group to a collaborator through `SearchStore`: `Results` to `SearchResults` (query, paging, explore, history picks; it owns `ResultRows`, which builds each row's badges and keeps them current), `Filters` to `SearchFilters` (a changed filter starts a new search through `onChange`), `Links` to `ClipboardLinks`, `RepoCard` to `RepoCardActions`. They are Koin factories in composeApp's `ViewModelsModule`, one set per ViewModel, since `SearchResults` and `ResultRows` hold paging and the signed-in login.

## Notes

- No search while typing: a search runs on IME submit, a history tap or a filter change, and needs at least 2 chars.
- GitHub source order: page cache (`CacheManager`) -> backend search (`BackendSearch`: platform + sort; skipped for `MostForks` and `Ios`) -> direct GitHub `/search/repositories` fallback (`GithubSearch`: `in:name,description archived:false fork:true`, `language:` qualifier). Fallback results are kept only when their latest stable release has an installer for the platform (`InstallerReleaseCheck.verifyBatch`, auto-skips up to 3 empty pages). The backend path does not send the language filter. Signed-in users get matching private repos prepended on page 1.
- Failures: a non-fallback backend status leaves as `BackendException`, a rate limit as `RateLimitException`, and a page-cache or search-history failure as `LocalStoreException`. A failed fallback page ends the results instead of failing the search. The screen shows rate limits with their own message and other failures through `errorMessage`.
- **Source toggle (`SearchSourceUi`):** UI surfaces `GitHub`, `Codeberg`, and per-user-added `CustomForge(host)` chips (from `TweaksRepository.getCustomForgeHosts()`). `state.selectedSource` -> `SearchRequest.source`. Forgejo branch in `SearchRepositoryImpl` hits `ForgejoClientRegistry.clientFor(host)` (`/repos/search`) with query + page only (platform / language / sort ignored), no backend mediator.
- `SearchDerivedState` filters `state.repositories` into `visibleRepos` by `hiddenRepoIds` AND (when `isHideSeenEnabled`) `seenRepoIds`. Unhide restores without re-fetch.
- Empty-grid-after-Hide-seen banner offers one-tap reset (issue #574) -> `OnDisableHideSeenForResults`, which turns the Hide seen setting off.
- Long-press card -> `DiscoveryRepoCard`'s own actions sheet (core/presentation): share, open on GitHub, seen toggle, hide.
- `DiscoveryRepositoryUi.isCurrentUserOwner` flipped by `ResultRows` as the signed-in user changes (E20).
- Not a bottom-bar tab (`BottomNavigationUtils.allowedScreens` leaves it out): on a phone it is pushed from the Classic tab card's search field or Manga's search button on Explore and Top Charts, and the Desktop sidebar keeps it as a destination.
- The query field and the filters button are `KomiTopBar`'s `search` and `actions`, one row at the top with a back arrow (none on Desktop).
- Clipboard auto-detect (gated by `TweaksRepository.getAutoDetectClipboardLinks()`) surfaces GitHub URLs from clipboard as a dismissible banner.
