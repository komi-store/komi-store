# Search Feature

Repo search with source / platform / language / sort filters. Paginated results.

## Structure

```text
feature/search/
  domain/   # package zed.rainxch.domain (not zed.rainxch.search.domain): SearchRepository, SearchHistoryRepository; SearchSource, ProgrammingLanguage, SortBy, SortOrder, ExploreResult
  data/     # SearchRepositoryImpl, SearchHistoryRepositoryImpl, dto/, di
  presentation/
    SearchViewModel / State / Action / Event / Root
    model/ + mappers/  # UI models (SearchSourceUi, SortByUi, ...) and toDomain()
    components/        # filters, sort and language sheets, search history
```

Platform filter type is `DiscoveryPlatform` (core/domain).

`SearchSource` is `GitHub` or `Forgejo(host)`; Codeberg is `Forgejo("codeberg.org")`.

## Navigation

`GithubStoreGraph.SearchScreen(initialPlatform: String?)`. The name is parsed to `DiscoveryPlatform` and passed to the VM via `parametersOf`. No caller sets it today. On Desktop the VM follows the sidebar's `BrowseFilterStore.platform` instead (`observeBrowseFilter`).

## Notes

- No search while typing: a search runs on IME submit, a history tap or a filter change, and needs at least 2 chars.
- GitHub source order: page cache (`CacheManager`) -> backend search (platform + sort; skipped for `MostForks` and `Ios`) -> direct GitHub `/search/repositories` fallback (`in:name,description archived:false fork:true`, `language:` qualifier). Fallback results are kept only when their latest stable release has an installer for the platform (`verifyBatch`, auto-skips up to 3 empty pages). The backend path does not send the language filter. Signed-in users get matching private repos prepended on page 1.
- **Source toggle (`SearchSourceUi`):** UI surfaces `GitHub`, `Codeberg`, and per-user-added `CustomForge(host)` chips (from `TweaksRepository.getCustomForgeHosts()`). `state.selectedSource` -> repository's `source: SearchSource` param. Forgejo branch in `SearchRepositoryImpl` hits `ForgejoClientRegistry.clientFor(host)` (`/repos/search`) with query + page only (platform / language / sort ignored), no backend mediator.
- `computeVisibleRepos` filters `state.repositories` at render time by `hiddenRepoIds` AND (when `isHideSeenEnabled`) `seenRepoIds`. Unhide restores without re-fetch.
- Empty-grid-after-Hide-seen banner offers one-tap reset (issue #574) -> `OnDisableHideSeenForResults`, which turns the Hide seen setting off.
- Long-press card -> `DiscoveryRepoCard`'s own actions sheet (core/presentation): share, open on GitHub, seen toggle, hide.
- `DiscoveryRepositoryUi.isCurrentUserOwner` flipped by `observeCurrentUser` (E20).
- Not a bottom-bar tab (`BottomNavigationUtils.allowedScreens` leaves it out): on a phone it is pushed from the Classic tab card's search field or Manga's search button on Explore and Top Charts, and the Desktop sidebar keeps it as a destination.
- The query field and the filters button are `KomiTopBar`'s `search` and `actions`, one row at the top with a back arrow (none on Desktop).
- Clipboard auto-detect (gated by `TweaksRepository.getAutoDetectClipboardLinks()`) surfaces GitHub URLs from clipboard as a dismissible banner.
