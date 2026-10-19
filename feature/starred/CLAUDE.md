# Starred Feature

Locally cached view of the signed-in user's GitHub stars. All code is in `presentation/` (the `domain/` and `data/` modules are empty); it uses `StarredRepository` from `core/domain` directly.

## Structure

```
feature/starred/presentation/
├── StarredReposViewModel / State / Action / Root
├── model/        # StarredRepositoryUi, StarredSortRule
├── mappers/StarredRepoToUiMapper
└── components/StarredRepositoryItem
```

## Deps

- `StarredRepository`, `FavouritesRepository`, `UserSessionRepository` (core/domain)
- `TweaksRepository`: persisted sort rule (`getStarredSortRule`)
- Local Room (`StarredRepoDao` in core/data)

## Navigation

`GithubStoreGraph.StarredReposScreen`.

## Notes

- Signed-out users get a sign-in prompt; nothing loads.
- Room is the read source. On first open the VM syncs only when `needsSync()` (last sync older than 24h); pull-to-refresh and retry force it. Other syncs: Details after a star toggle (forced) and the Apps starred picker. No background sync.
- `syncStarredRepos` pages `/user/starred`, keeps only repos whose latest stable release has an installer for the current platform, and replaces the whole table. Starred repos without one never show here.
- Starring happens in Details (`setStarred`). This screen does not unstar; the heart on each row toggles favourites.
- Inline search bar when the list is non-empty; filters name / owner / description / language client-side. `OnRefresh` clears the active query so the refreshed list isn't masked behind a stale filter.
- `StarredRepositoryUi.isCurrentUserOwner` set when the signed-in user owns the repo.
