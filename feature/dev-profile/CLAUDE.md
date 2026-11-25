# Dev Profile Feature

GitHub user / org profile view: avatar, bio, stats, contribution calendar, and their repos with search / filter / sort.

## Structure

```
feature/dev-profile/
├── domain/   # DeveloperProfileRepository; DeveloperProfile, DeveloperRepository, ContributionCalendar / ContributionDay, RepoFilterType, RepoSortType
├── data/     # impl + dto + mappers + di
└── presentation/
    ├── DeveloperProfileViewModel / State / Action / Root
    ├── model/ + mappers/  DeveloperProfileUi, DeveloperRepositoryUi, ContributionCalendarUi and their toUi()
    └── components/  ProfileInfoCard, ContributionCalendar, FilterSortControls, DeveloperRepoItem
```

## Navigation

`GithubStoreGraph.DeveloperProfileScreen(username: String)`. `username` reaches the VM through `koinViewModel { parametersOf(args.username) }`.

## Notes

- Load order: profile first; on success the contribution calendar starts in its own coroutine while repos load. A profile failure stops there.
- All three calls return `Result<T>`, but profile and repos rethrow `RateLimitException`; the VM catches it and stops loading without an error message. Other failures are typed: `NetworkException` for the remote calls, `LocalStoreException` for the installed-app (`localStore`) and favourites reads. Contributions return a rate limit as a failure.
- The state holds UI models mapped in the VM. The contribution grid gets its levels and the first day's weekday row (0 for Sunday) from `ContributionCalendar.toUi()`; its colors are `contributionLevels` in the personality package.
- Profile and repo list go backend first (`BackendApiClient.getUser` / `getUserRepos`), GitHub direct on `shouldFallbackToGithubOrRethrow`. Repos are owner-type, archived and forks dropped, then every repo gets a release check (latest 10 releases, `Semaphore(20)`) for `hasReleases` / `hasInstallableAssets` / `latestVersion`; `isInstalled` / `isFavorite` come from local Room.
- Contribution calendar comes from third-party `github-contributions-api.jogruber.de` and is skipped when `DeveloperProfile.isOrganization`. Both the backend and the GitHub-direct mapper set `userType` from the response `type`.
- Client-side search (name / description), filter (`RepoFilterType`: `WITH_RELEASES` default, `WITH_INSTALLABLE`, `INSTALLED`, `FAVORITES`) and sort (`RepoSortType`: `UPDATED` default, `STARS`, `NAME`).
