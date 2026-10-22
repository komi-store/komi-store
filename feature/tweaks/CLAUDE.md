# Tweaks Feature

Single home for app-level settings: appearance, language, proxy, GitHub mirror, custom forges, translation, installer choice (Default / Shizuku / Dhizuku / Root), update checks, seen / hidden / skipped lists, access tokens, feedback, plus the About and Licenses screens. Presentation-only module.

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

Android: one scrolling column of Look & feel, Connectivity, Installs & updates, Privacy & data, App. Desktop: the same minus Installs & updates, one section at a time from a sidebar. Drill-ins: Mirror picker + custom forges (Connectivity), Skipped updates (Installs & updates), Hidden repos + Access tokens (Privacy & data), Feedback (App).

## Sub-screen VMs (registered in `composeApp/.../app/di/ViewModelsModule.kt`)

`SkippedUpdatesViewModel` (unskip = `setSkippedReleaseTag(pkg, null)` then `checkForUpdates`), `HiddenRepositoriesViewModel` (`HiddenReposRepository.unhide` / `clearAll`), `HostTokensViewModel` (`HostTokenRepository`), `FeedbackViewModel`, `MirrorPickerViewModel` (explicit `viewModel {}`; measures every mirror on open through `MirrorRepository.measureLatencies()`, Direct included; "Pick fastest" (`OnAutoPickFastest`) applies `fastestReleaseMirror` once; the `named("test")` HttpClient only backs the 5 s "Test connection"), `AppInfoViewModel`, `LicensesViewModel`.

## Navigation

`TweaksScreen`, `SkippedUpdatesScreen`, `HiddenRepositoriesScreen`, `HostTokensScreen`, `MirrorPickerScreen`, `AboutScreen` (opened from Profile and the desktop menu), `LicensesScreen` (from About).

## Notes

- `include_pre_releases` is only a default: new `InstalledApp` rows (install in `InstallationManagerImpl`, link in `AppsRepositoryImpl.linkAppToRepo`) copy it into `includePreReleases`. Existing rows keep their per-app value.
- **Proxy:** a master config plus one config per `ProxyScope` (`DISCOVERY` / `DOWNLOAD` / `TRANSLATION`). Only the per-scope configs reach `ProxyManager`; `TweaksViewModel` copies the master into every scope flagged use-master when it is saved. `ProxyRepository.setMasterProxyConfig` alone changes no traffic.
- **Repository layout (Look & feel):** `OnRepoLayoutSelected` writes `TweaksRepository.setRepoGridLayout`, the list or grid that Explore, Top Charts, Search and the category list read. Those screens have no toggle of their own.
- **Content width (desktop only, Look & feel):** `OnContentWidthSelected` persists `ContentWidth` (`COMPACT` / `WIDE` / `EXTRA_WIDE`). `MainViewModel` feeds it to `LocalContentWidth` (provided in `AppNavigation`); screens read it through `contentWidthCap()` / `Modifier.constrainedContentWidth()` (core/presentation).
- **Custom forges (Connectivity):** `CustomForgesDialog` adds / removes hosts in `TweaksRepository`; the host rows under the entry can also remove. Hosts become `SearchSourceUi.CustomForge` chips in Search and are passed to `RepositoryUrlParser.parse` by the Apps link sheet and external import.
- **Access Tokens (`HostTokensScreen`):** lists / adds / edits / deletes / validates per-host PATs in `HostTokenRepository`. Saved tokens are sent to their host by `HostTokenInterceptor` (`docs/agents/forges.md`).
- **Translation provider:** Google and LibreTranslate switch immediately. Youdao, DeepL and Microsoft with a required credential field blank only set `draftTranslationProvider` (form shown, persisted provider unchanged); the switch happens on that provider's credentials save.
- **Language:** `OnAppLanguageSelected` persists the tag. Android applies it from `MainActivity` (collects `getAppLanguage()`, then `recreate()`). Desktop gets `OnAppLanguageChangeRequiresRestart`; the toast action calls `restartAppAfterLanguageChange()` (relaunch + `exitProcess`). The Android actual is empty and never called.
