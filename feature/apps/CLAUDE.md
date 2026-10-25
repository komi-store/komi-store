# Apps Feature

Installed-apps manager. Lists apps tracked by Komi Store, launches them, checks and installs updates, links apps installed elsewhere to a repo. In the Android bottom nav (label "Library"). Android only: Desktop hands the downloaded installer to the OS and never records an installed app, so the Desktop sidebar and menu bar leave Library out.

## Structure

```
feature/apps/
├── domain/   # AppsRepository, GithubRepoInfo, ImportResult / ImportFormat
├── data/     # AppsRepositoryImpl + di
└── presentation/
    ├── AppsViewModel / State / Action / Event / Root
    ├── components/  LibraryRow (+ LibraryAppRow, LibraryDownloadRow, LibraryGroup), LinkAppBottomSheet, AdvancedAppSettingsBottomSheet, VariantPickerDialog, ImportSummarySheet
    ├── import/      # ExternalImportRoot/ViewModel: scan device for untracked apps, auto-link or review repo matches
    └── starred/     # StarredPickerRoot/ViewModel: APK-shipping subset of user's GitHub stars
```

## Navigation

`GithubStoreGraph.AppsScreen`, `ExternalImportScreen`, `StarredPickerScreen`.

## Notes

- `AppsViewModel` is created in `AppNavigation` (not by `AppsRoot`) and its state is collected there for the bottom-nav update badge, so it loads at app start and outlives the screen.
- Uses `InstalledAppsRepository` + `SyncInstalledAppsUseCase`. `openApp` via `AppLauncher`. `getLatestRelease` / `fetchRepoInfo` go backend first, GitHub direct on fallback; a non-null `sourceHost` routes them to the `ForgejoClientRegistry` client instead. `linkAppToRepo` only persists `sourceHost` on `InstalledApp.sourceHost`.
- **Stored match:** Update and re-download start from the release and asset the last update check stored on the row (`resolveUpdateTarget` -> `storedUpdateTarget`). `getLatestRelease` runs only when nothing is stored, or after a download from the stored match failed (`storedMatchFailures`).
- **Missing file:** Install on a pending row whose `pendingInstallFilePath` is gone calls `redownloadPendingApp`, which downloads again in the pinned variant; the row stays parked. When no release resolves (an empty window, which a failed fetch also returns), Install and Update show `apps_no_release_found` once and leave the park alone.
- **Ready to install** needs a live park, `isPendingInstall` with a file path: the sync keeps a file whose park was cleared until it can prove the install, and that file is not offered.
- **Downloads:** in-progress downloads come from `DownloadOrchestrator.downloads` whoever started them, Details included (`observeOrchestratorDownloads`), and render as `LibraryDownloadRow`s. The app's own row is hidden while its download row is shown.
- **`sourceHost` propagation:** `AppsAction.OnNavigateToRepo` + `AppsEvent.NavigateToRepo` carry `sourceHost`, owner, repo, packageName; `AppsRoot.onNavigateToRepo` is `(repoId, sourceHost, owner, repo, packageName) -> Unit` and routes to `GithubStoreGraph.DetailsScreen(sourceHost = ..., packageName = ...)`.
- **Multi-source backend match:** link suggestions (`RepoMatchSuggestion`) may come from GitHub *or* a Forgejo host; the link sheet shows a source host chip per row (GitHub / Codeberg / host) so the user picks the right forge variant.
- **Grouped by repo:** apps sharing a `repoId` render together inside each section (`AppsState.pendingGroups` / `updateGroups` / `idleGroups` from `groupedByRepo()`, which keeps each group at its first member's sort position). A group of several apps is one `LibraryGroup` card (owner/repo header, unframed rows with `LibraryGroupDivider`) in every section; a single app is its own framed row. `InstalledAppIcon` lives in `core/presentation`.
- **Item rows:** every item is a `LibraryRow`: icon, name, owner + `SourceChip`, one status line (plain, accent for an update or install, error, downgrade), tags for the app's non-default settings (pre-release, updates ignored, skipped tag, variant, asset filter), a progress bar while checking / downloading / installing, and actions at the end. `LibraryAppRow` (tracked apps) has one main action (Install, Update or Open) and one menu with every per-app action; `LibraryDownloadRow` has the stage's actions (pause / resume, Install, Retry) and a close that deletes the task (dismisses while installing).
- **Review stars:** `refreshRatings` asks `ReviewsRepository.getSummaries` for every tracked repo whenever that set changes, and `AppItem.ratingAverage` / `ratingCount` feed a `CompactRating` on the owner line of a single row, or on the `AppGroupHeader` of a group. A failed call leaves the stars off and retries on the next change.
- Sort + search: `AppSortRule` enum (UpdatesFirst default, RecentlyUpdated, Name), persisted via `TweaksRepository.getAppsSortRule` / `setAppsSortRule`. Inline search filters appName / repoOwner. In Classic the field is `AppsTopbar`'s `search`, so it sits in the tab card; elsewhere it stays above the list.
- Per-app actions: Ignore-updates (`updateCheckEnabled`), Skip-this-release (per-tag, cleared by a newer release), per-app pre-releases, Advanced filter (regex on asset names + fallback to older releases), Pin variant (`VariantPickerDialog` saves only the variant string, clearing the token-set + glob fingerprint stored at link time).
- Link sheet (`LinkAppBottomSheet`) sorts device apps by `InstallerCategory.sortPriority` (`core/domain`): SIDE_STORE -> SIDELOADED -> PLAY_STORE -> VENDOR_STORE -> SYSTEM_UPDATE, then name. `InstallerCategory.classify` uses `DeviceApp.installerPackageName` + `isUpdatedSystemApp`, with a package-name prefix fallback for vendor / system apps. Each row shows a tonal category chip.
- Auto-check on resume: `AppsRoot` sends `OnLifecycleResume`, which runs `autoCheckForUpdatesIfNeeded` (skipped if the last auto-check, or the oldest tracked app's `lastCheckedAt`, is under 30 min old).
- Backup import / export (`AppsViewModel` + `AppsRepository`): export native JSON or Obtainium JSON; import auto-detects either format, then `ImportSummarySheet` shows buckets imported / skipped (already tracked) / not on this phone / non-GitHub / failed. An app missing from the device is never imported: a row means installed, and the sync keeps such rows when most tracked apps are missing (GH#748), so they'd show Open and Uninstall.
- External import (`import/`): `ExternalImportRepository.runFullScan` finds untracked device apps; candidates whose top match has confidence >= 0.85 are auto-linked, the rest become review cards (pick suggestion, search override, skip, skip forever, undo). **Skip-on-slow-scan:** after 5s `state.isSkipAvailable` flips true; `OnSkipLongScan` cancels the scan and builds cards from what has resolved so far. "Add manually" returns to Apps and opens the link sheet via the `EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY` saved-state flag.
- Starred picker (`starred/`): checks each starred repo's latest release for `.apk` / `.apks` / `.xapk` / `.aab` assets. Stops on `RateLimitException`; `OnResume` rescans only repos with no release tag yet.
