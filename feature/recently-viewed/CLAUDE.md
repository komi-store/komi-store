# Recently Viewed Feature

Local "recently viewed" history. **Presentation-only**, backed by `SeenReposRepository`, the same table that drives the "Hide seen" filter on Home / Search / Feed.

## Structure

```
feature/recently-viewed/presentation/
├── RecentlyViewedViewModel / State / Action / Root
├── model/RecentlyViewedRepo, HistorySection, mappers/RecentlyViewedRepoMapper
└── components/RecentlyViewedItem
```

## Navigation

`GithubStoreGraph.RecentlyViewedScreen`.

## Notes

- Rows come from `SeenReposRepository.markAsSeen`: `DetailsViewModel` calls it when a repo loads and again once the releases are read (filling the newest stable tag and its platforms), and the "Mark as seen" card action on Home / Search / Feed writes the same table. So the list is not only repos whose Details was opened.
- No network. Rows are removed one at a time (`removeFromHistory(repoId)`). `OnClearAllHistory` exists but nothing in the UI sends it; clearing all seen history is in Tweaks (Privacy & data).
- The Details link recovers the host with `RepoIdCodec.sourceHostOf(repoId, repoUrl)`. Since DB v22 `SeenRepo` also keeps stars, platforms, the newest version and the host, saved by `markAsSeen`; rows from before stay sparse until the repo is seen again.
- Rows are `KomiLibraryRow` (core/presentation): the app's icon (the installed one when it's on the device) on a cookie plate, name, owner, description, then install status, stars, platforms and the newest version, with when the list got the repo underneath. Install status is live: the VM combines the list with `InstalledAppsRepository.getAllInstalledApps()` through `installsByRepo()`.
- The list is grouped by day (Today, Yesterday, This week as the last seven days, Earlier) in `RecentlyViewedViewModel.byDay`, shown as full-width headers in the grid. Swiping a row toward the start removes it; the X does the same.
