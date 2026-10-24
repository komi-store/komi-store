# Tweaks Feature

Single home for app-level settings: appearance, language, proxy, GitHub mirror, custom forges, translation, installer choice (Default / Shizuku / Dhizuku / Root / Third-party), update checks, seen / hidden / skipped lists, access tokens, feedback, plus the About and Licenses screens. Presentation-only module.

## Structure

```
feature/tweaks/presentation/
├── TweaksViewModel / State / Action / Event / Root, RestartApp (expect/actual)
├── components/
│   ├── sections/    # TweaksSections.kt groups the per-section content files into the sections below
│   ├── shell/       # SettingsGroup / SettingsRow / SettingsDrillRow kit, also used by the sub-screens
│   ├── desktop/     # TweaksDesktopContent: sidebar (DesktopSection) over the same section composables
│   └── appearance/  # personality + accent pickers
├── appinfo/       # AppInfoRoot (About)
├── licenses/      # LicensesRoot, reads core/presentation composeResources files/licenses.json
├── feedback/      # FeedbackViewModel + bottom sheet
├── hidden/        # HiddenRepositoriesRoot
├── skipped/       # SkippedUpdatesRoot
├── hosttokens/    # HostTokensRoot (per-host PATs)
├── mirror/        # MirrorPickerRoot
├── connection/    # paste-a-proxy-URL sheet
├── model/         # ProxyScopeFormState, ProxyType, LanguagePickerOption
└── utils/         # TweaksDeepLinkBus: desktop Help > Feedback opens the feedback sheet
```

## Sections

Android: one scrolling column of Look & feel, Connectivity, Installs & updates, Privacy & data, App, Developer & diagnostics. Desktop: the same minus Installs & updates and Developer & diagnostics, one section at a time from a sidebar. Drill-ins: Mirror picker + custom forges (Connectivity), Skipped updates (Installs & updates), Hidden repos + Access tokens (Privacy & data), Feedback (App).

## Sub-screen VMs (registered in `composeApp/.../app/di/ViewModelsModule.kt`)

`SkippedUpdatesViewModel` (unskip = `setSkippedReleaseTag(pkg, null)` then `checkForUpdates`), `HiddenRepositoriesViewModel` (`HiddenReposRepository.unhide` / `clearAll`), `HostTokensViewModel` (`HostTokenRepository`), `FeedbackViewModel`, `MirrorPickerViewModel` (explicit `viewModel {}`; measures every mirror on open through `MirrorRepository.measureLatencies()`, Direct included (`MirrorRepositoryImpl` adds the bundled `direct` entry to a catalog that lacks it); "Pick fastest" (`OnAutoPickFastest`) applies `fastestReleaseMirror` once; the `named("test")` HttpClient only backs the 5 s "Test connection"), `AppInfoViewModel`, `LicensesViewModel`.

## Navigation

`TweaksScreen`, `SkippedUpdatesScreen`, `HiddenRepositoriesScreen`, `HostTokensScreen`, `MirrorPickerScreen`, `AboutScreen` (opened from Profile and the desktop menu), `LicensesScreen` (from About).

## Notes

- `include_pre_releases` is only a default: new `InstalledApp` rows (install in `InstallationManagerImpl`, link in `AppsRepositoryImpl.linkAppToRepo`) copy it into `includePreReleases`. Existing rows keep their per-app value.
- **Proxy:** a master config plus one config per `ProxyScope` (`DISCOVERY` / `DOWNLOAD` / `TRANSLATION`). Only the per-scope configs reach `ProxyManager`; `TweaksViewModel` copies the master into every scope flagged use-master when it is saved. `ProxyRepository.setMasterProxyConfig` alone changes no traffic.
- **Repository layout (Look & feel):** `OnRepoLayoutSelected` writes `TweaksRepository.setRepoGridLayout`, the list or grid that Explore, Top Charts, Search and the category list read. Those screens have no toggle of their own.
- **Content width (desktop only, Look & feel):** `OnContentWidthSelected` persists `ContentWidth` (`COMPACT` / `WIDE` / `EXTRA_WIDE`). `MainViewModel` feeds it to `LocalContentWidth` (provided in `AppNavigation`); screens read it through `contentWidthCap()` / `Modifier.constrainedContentWidth()` (core/presentation).
- **Refresh rate (Android only, Look & feel):** the row lists `rememberDisplayModeOptions()` (core/presentation; empty on desktop, which hides the row) plus Automatic. `OnDisplayModeSelected` persists the mode id (`SYSTEM_DEFAULT_DISPLAY_MODE_ID` = Automatic); `MainActivity` applies it to `preferredDisplayModeId` and falls back to the system default for an id the display no longer reports. Some devices (HyperOS) accept the request but keep the panel at its peak rate.
- **Custom forges (Connectivity):** `CustomForgesDialog` adds / removes hosts in `TweaksRepository`; the host rows under the entry can also remove. Hosts become `SearchSourceUi.CustomForge` chips in Search and are passed to `RepositoryUrlParser.parse` by the Apps link sheet and external import.
- **Access Tokens (`HostTokensScreen`):** lists / adds / edits / deletes / validates per-host PATs in `HostTokenRepository`. Saved tokens are sent to their host by `HostTokenInterceptor` (`docs/agents/forges.md`).
- **Translation provider:** Google and LibreTranslate switch immediately. Youdao, DeepL and Microsoft with a required credential field blank only set `draftTranslationProvider` (form shown, persisted provider unchanged); the switch happens on that provider's credentials save.
- **Third-party installer:** the picked package is stored as `external_installer_package` in `TweaksRepository`. `ExternalInstallerDiscovery` lists apps with an APK `ACTION_VIEW` / `INSTALL_PACKAGE` filter, minus Komi and system apps. `SilentInstallerDispatcher` hands the APK over with `setPackage`, skipping Komi's own "Install unknown apps" check while an app is picked, and falls back to the Default install when nothing is picked or the app cannot be opened.
- **Language:** `OnAppLanguageSelected` persists the tag. Android applies it from `MainActivity` (collects `getAppLanguage()`, then `recreate()`). Desktop gets `OnAppLanguageChangeRequiresRestart`; the toast action calls `restartAppAfterLanguageChange()` (relaunch + `exitProcess`). The Android actual is empty and never called.
- **Diagnostic logs (Android only, Developer & diagnostics):** always on, with no switch. `DiagnosticSession.install` (in `GithubStoreApp`) adds a Kermit `LogWriter` that mirrors redacted records from a single writer thread into `filesDir/diagnostics/app.log`. Each process start appends a session header instead of truncating, so the run that crashed is still there; the file is trimmed to 1 MB once it passes 2 MB. `CrashLogs` (core/data androidMain) writes uncaught exceptions to `filesDir/crashes` and keeps the newest 3. Share bundles the log and the latest crash report with app / device / OS details, redacted, through the existing `ShareManager` FileProvider export; nothing is uploaded. This is the only place crash reports are shared from.
