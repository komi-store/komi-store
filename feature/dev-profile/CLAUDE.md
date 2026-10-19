# Dev Profile Feature

GitHub user / org profile view: avatar, bio, stats, contribution calendar, and their repos with search / filter / sort.

## Structure

```
feature/dev-profile/
├── domain/   # DeveloperProfileRepository; DeveloperProfile, DeveloperRepository, ContributionCalendar / ContributionDay, RepoFilterType, RepoSortType
├── data/     # impl + dto + mappers + di
└── presentation/
    ├── DeveloperProfileViewModel / State / Action / Root
    └── components/  ProfileInfoCard, ContributionCalendar, FilterSortControls, DeveloperRepoItem
```

## Navigation

`GithubStoreGraph.DeveloperProfileScreen(username: String)`. `username` reaches the VM through `koinViewModel { parametersOf(args.username) }`.

## Notes

- Load order: profile first; on success the contribution calendar starts in its own coroutine while repos load. A profile failure stops there.
- All three calls return `Result<T>`, but profile and repos rethrow `RateLimitException`; the VM catches it and stops loading without an error message.
- Profile and repo list go backend first (`BackendApiClient.getUser` / `getUserRepos`), GitHub direct on `shouldFallbackToGithubOrRethrow`. Repos are owner-type, archived and forks dropped, then every repo gets a release check (latest 10 releases, `Semaphore(20)`) for `hasReleases` / `hasInstallableAssets` / `latestVersion`; `isInstalled` / `isFavorite` come from local Room.
- Contribution calendar comes from third-party `github-contributions-api.jogruber.de` and is skipped when `DeveloperProfile.isOrganization`. Both the backend and the GitHub-direct mapper set `userType` from the response `type`.
- Client-side search (name / description), filter (`RepoFilterType`: `WITH_RELEASES` default, `WITH_INSTALLABLE`, `INSTALLED`, `FAVORITES`) and sort (`RepoSortType`: `UPDATED` default, `STARS`, `NAME`).
