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
- `FavoriteRepo` stores no `sourceHost`; the Details link recovers it with `RepoIdCodec.sourceHostOf(repoId, repoUrl)`.
- Inline search bar when the list is non-empty; filters name / owner / description / language client-side.
- `FavouriteRepository.isCurrentUserOwner` set when the signed-in user owns the repo.
- Import stars: `fetchStarredForUsername` pages through `/users/{name}/starred` and keeps only repos whose latest stable release has an installer asset for any platform (`.apk`, `.exe`, `.dmg`, `.deb`, ...). Candidates already favourited are marked; "Add all" favourites the rest.
