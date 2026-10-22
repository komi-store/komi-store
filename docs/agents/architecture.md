# Architecture

Clean Architecture with MVVM. **Domain** holds contracts, models and pure logic. **Data** implements them with Ktor, Room and Koin. **Presentation** holds ViewModels (`StateFlow` state, `Channel` events) and Compose UI. The ViewModel and screen shapes are in `docs/code-style.md`, sections 4 and 8.

## Modules

```text
androidApp/            Android application: MainActivity, GithubStoreApp, manifest, resources, R8 rules
composeApp/            shared app code, navigation, DI wiring, Desktop entry point (commonMain / jvmMain)
core/domain            interfaces, models, use cases (no framework deps)
core/data              repositories, Ktor, Room, Koin, platform implementations
core/data-aidl         the Shizuku and Dhizuku installer AIDL interfaces (Android only)
core/presentation      Personality design system (Classic / Manga), Komi* components, 14-locale strings
feature/               apps auth details dev-profile favourites feed home profile recently-viewed repo-pages search starred tweaks
konsist-test/          architecture rules
build-logic/convention convention plugins
```

Each feature has up to three sub-modules: `domain/`, `data/`, `presentation/`. `profile`, `recently-viewed` and `tweaks` are presentation-only. `favourites` and `starred` also have `domain/` and `data/` modules, currently empty.

## Navigation

Routes are members of the `@Serializable` sealed interface `GithubStoreGraph` in `composeApp/.../app/navigation/GithubStoreGraph.kt`; `AppNavigation.kt` wires them. Two route arguments matter beyond their names:

- `DetailsScreen.packageName` (set from Apps) opens Details on that installed app when a repository ships several apps.
- `DetailsScreen.sourceHost` is non-null for Codeberg, Forgejo and custom-forge repositories and routes every `DetailsRepository` call through `ForgejoClientRegistry` instead of the GitHub-backed path.

`RepoPullsScreen` is registered but nothing navigates to it yet.

## DI

Koin. Each feature's data module declares its bindings in `data/di/SharedModule.kt`. ViewModels are registered in `composeApp/.../app/di/ViewModelsModule.kt` (`viewModelOf(::X)`, or `viewModel { ... }` when they take route arguments). Modules are assembled in `initKoin.kt`.

## Core repositories (`core/domain`)

`FavouritesRepository`, `StarredRepository`, `InstalledAppsRepository`, `SeenReposRepository`, `HiddenReposRepository`, `TweaksRepository`, `UserSessionRepository` (signed-in user, token, session expiry), `ProxyRepository`, `RateLimitRepository`, `ExternalImportRepository`, `MirrorRepository`, `AnnouncementsRepository`, `CacheRepository`, `BrowseFilterStore`, `HostTokenRepository`. `SearchHistoryRepository` lives in `feature/search/domain`.

System interfaces with Android and Desktop implementations: `Installer`, `InstallerStatusProvider`, `PackageMonitor`, `SystemInstallSerializer`.

## Convention plugins (`build-logic/convention/`)

`convention.kmp.library` (domain and data), `convention.cmp.library` (core/presentation and composeApp), `convention.cmp.feature` (feature presentation), `convention.android.application.compose` (androidApp), `convention.android.library` (core/data-aidl), `convention.room`, `convention.buildkonfig`, `convention.quality` (detekt and Kover, applied by the library and app conventions). The Compose conventions also apply the stability analyzer; KMP modules use the Android KMP library plugin (`com.android.kotlin.multiplatform.library`), configured in `configureAndroidTarget`; it creates lint tasks only because `com.android.lint` is applied as well, which also adds a `lintJvm` that CI doesn't run. Lint options are shared in `configureLint`.

## Adding a feature

1. Create `feature/<name>/{domain,data,presentation}/` with the matching convention plugin.
2. `include` the modules in `settings.gradle.kts`.
3. Domain interfaces, then the implementation and its Koin module in `data/di/SharedModule.kt`, then the ViewModel and screen.
4. Add the route to `GithubStoreGraph.kt`, wire it in `AppNavigation.kt`, register the ViewModel in `ViewModelsModule.kt` and the data module in `initKoin.kt`.
5. Add a `feature/<name>/CLAUDE.md` with what a reader can't see from the code in a minute.
