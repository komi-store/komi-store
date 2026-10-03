# Apps Feature

Installed-apps manager. Lists apps installed through GHS, launches them, checks updates. Android-only in nav (bottom-nav hidden on Desktop).

## Structure

```
feature/apps/
├── domain/repository/AppsRepository.kt
├── data/  AppsRepositoryImpl + di
└── presentation/
    ├── AppsViewModel / State / Action / Event / Root
    ├── components/  app item cards, update badges, LinkAppBottomSheet, AdvancedAppSettingsBottomSheet, ApkInspectSheet, import banner
    ├── import/      # ExternalImportRoot/ViewModel — Obtainium import/export + manual-link
    └── starred/     # StarredPickerRoot/ViewModel — APK-shipping subset of user's GitHub stars
```

## Key interface

```kotlin
interface AppsRepository {
    suspend fun getApps(): Flow<List<InstalledApp>>
    suspend fun openApp(installedApp: InstalledApp, onCantLaunchApp: () -> Unit = {})
    suspend fun getLatestRelease(owner, repo, includePreReleases = false, sourceHost: String? = null): GithubRelease?
    suspend fun fetchRepoInfo(owner, repo, sourceHost: String? = null): GithubRepoInfo?
    suspend fun linkAppToRepo(..., sourceHost: String? = null)
    /* exportApps / exportObtainium / importApps */
}
```

## Navigation

`GithubStoreGraph.AppsScreen`, `ExternalImportScreen`, `StarredPickerScreen`.

## Notes

- Uses `InstalledAppsRepository` + `SyncInstalledAppsUseCase`. `openApp` via `AppLauncher`. `PackageMonitor` + `Installer` (Android). Data layer injects `ForgejoClientRegistry`; non-null `sourceHost` on `getLatestRelease` / `fetchRepoInfo` / `linkAppToRepo` routes to the Forgejo client (else default GitHub-direct path).
- **`sourceHost` propagation:** `InstalledApp.sourceHost` persisted on link; `AppsAction.OpenAppDetails` / `OpenRepoDetails` + `AppsEvent.NavigateToRepo` carry `sourceHost`; `AppsRoot.onNavigateToRepo` is `(repoId, sourceHost, owner, repo, packageName) -> Unit` and routes to `GithubStoreGraph.DetailsScreen(sourceHost = …, packageName = …)`.
- **Multi-source backend match:** `RepoSuggestion` candidates may come from GitHub *or* a Forgejo host; the link sheet displays the source host chip per row so the user picks the right forge variant.
- **Grouped by repo:** apps sharing a `repoId` render together inside each section (`AppsState.pendingGroups` / `updateGroups` / `idleGroups` from `groupedByRepo()`, which keeps each group at its first member's sort position). Up-to-date groups are one `AppGroupCard` (owner/repo header + unframed `CompactAppRow`s with `AppGroupDivider`); update and pending groups are an `AppGroupStack` (header above the cards). `InstalledAppIcon` lives in `core/presentation`.
- Sort + search: `AppSortRule` enum (UpdatesFirst default, AlphabeticalAZ, RecentlyAdded, RecentlyUpdated). Persisted in DataStore. Inline search filters appName / packageName.
- Per-app actions: Ignore-updates (silence badge), Skip-this-release (per-tag, auto-clear on next release), Advanced filter (regex on asset names + monorepo fallback), Pin variant (token-set + glob fingerprint), Inspect APK (decoded manifest sheet).
- Link sheet (`LinkAppBottomSheet`) sorts installed apps by `InstallerCategory` (`core/domain`): SIDE_STORE (F-Droid / Obtainium / Aurora / Droid-ify / GHS itself) → SIDELOADED → VENDOR_STORE (Samsung / Huawei / Xiaomi / Oppo / vivo / Honor / Amazon) → PLAY_STORE → SYSTEM_UPDATE. `DeviceApp.installerPackageName` (via `PackageManager.getInstallSourceInfo` / pre-30 fallback) + `isUpdatedSystemApp` (via `FLAG_UPDATED_SYSTEM_APP`) feed the classifier. Each row shows a tonal source chip — F-Droid / Obtainium surfaces first; Samsung Authentication Framework and friends drop to the bottom.
- Auto-update on resume: `OnLifecycleResume` fires `autoCheckForUpdatesIfNeeded` (30-min cooldown) — catches drift after external install while GHS background-killed.
- External import (`import/`): Obtainium JSON import/export with pre-import summary buckets (imported / already-tracked / non-GitHub-skipped); manual-link-only path. **Skip-on-slow-scan:** after `~5s` `state.isSkipAvailable` flips true → user can fire `OnSkipLongScan` to abort the in-flight backend lookup and move on without waiting for the remaining candidates.
- Starred picker (`starred/`): scans signed-in user's GitHub stars, surfaces APK-shipping repos. Resumes mid-scan on rate-limit.
