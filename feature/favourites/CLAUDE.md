# Favourites Feature

Local saved favourites view, plus "Import stars" to favourite repos from any GitHub user's public stars. All code is in `presentation/` (the `domain/` and `data/` modules are empty); it uses `FavouritesRepository` from `core/domain` directly.

## Structure

```
feature/favourites/presentation/
├── FavouritesViewModel / State / Action / Root
├── model/        # FavouriteRepository (UI model), FavouritesSortRule
├── mappers/FavouriteRepositoryMapper
├── components/FavouriteRepositoryItem
└── import/       # ImportStarsRoot / ViewModel / State / Action / Event, ImportCandidateUi
```

## Deps

- `FavouritesRepository` (core/domain): list + toggle, backed by Room (`FavoriteRepoDao`)
- `UserSessionRepository` (core/domain): signed-in login for the self-owned badge
- `TweaksRepository`: persisted sort rule (`getFavouritesSortRule`)
- `StarredRepository.fetchStarredForUsername` (core/domain): Import stars only

## Navigation

`GithubStoreGraph.FavouritesScreen`, `ImportStarsScreen` (from the Favourites import button).

## Notes

- The favourites list itself is local Room; only Import stars hits the network.
- Favourites are added from Details, Dev Profile, Starred and Import stars. This screen lists and un-favourites (`OnToggleFavorite`).
- The Details link recovers the host with `RepoIdCodec.sourceHostOf(repoId, repoUrl)`. Since DB v22 `FavoriteRepo` also keeps stars, platforms and the host, filled by every place that favourites (Details, Dev Profile, Starred, Import stars); older rows stay sparse until favourited again.
- Rows are `KomiLibraryRow` (core/presentation): the app's icon (the installed one when it's on the device) on a cookie plate, name, owner, description, then install status, stars, platforms and the newest version, with when the list got the repo underneath. Install status is live: the VM combines the list with `InstalledAppsRepository.getAllInstalledApps()` through `installsByRepo()`. The heart is `KomiFavouriteToggle`.
- Inline search bar when the list is non-empty; filters name / owner / description / language client-side.
- `FavouriteRepository.isCurrentUserOwner` set when the signed-in user owns the repo.
- Import stars: `fetchStarredForUsername` pages through `/users/{name}/starred` and keeps only repos whose latest stable release has an installer asset for any platform (`.apk`, `.exe`, `.dmg`, `.deb`, ...). Candidates already favourited are marked; "Add all" favourites the rest.
