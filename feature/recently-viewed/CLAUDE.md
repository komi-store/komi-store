# Recently Viewed Feature

Local "recently viewed" history. **Presentation-only**, backed by `SeenReposRepository`, the same table that drives the "Hide seen" filter on Home / Search / Feed.

## Structure

```
feature/recently-viewed/presentation/
├── RecentlyViewedViewModel / State / Action / Root
├── model/RecentlyViewedRepo, mappers/RecentlyViewedRepoMapper
└── components/RecentlyViewedItem
```

## Navigation

`GithubStoreGraph.RecentlyViewedScreen`.

## Notes

- Rows come from `SeenReposRepository.markAsSeen`: `DetailsViewModel` calls it when a repo loads, and the "Mark as seen" card action on Home / Search / Feed writes the same table. So the list is not only repos whose Details was opened.
- No network. Rows are removed one at a time (`removeFromHistory(repoId)`). `OnClearAllHistory` exists but nothing in the UI sends it; clearing all seen history is in Tweaks (Privacy & data).
- `SeenRepo` stores no `sourceHost`; the Details link recovers it with `RepoIdCodec.sourceHostOf(repoId, repoUrl)`.
