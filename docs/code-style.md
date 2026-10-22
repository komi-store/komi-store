# Komi Store code style

Status: **adopted** on 2026-10-19 from the decisions in [findings.md](quality/findings.md). Some rules get their automated check in follow-up work; until then they apply to new and touched code. Links point at examples in [`cfa6bd30`](https://github.com/komi-store/komi-store/tree/cfa6bd30e6b35cbbc369d78a4599182a9a66beac).

This file is for people and agents writing code here. CLAUDE.md files point to it. A tag after a rule says what enforces it:

- **[Spotless]**: `./gradlew spotlessCheck` in CI: ktlint 1.8.0 with the Compose rules, on files changed against `main`
- **[detekt]**: `./gradlew detekt` in CI, with per-module baselines
- **[Konsist]**: `./gradlew :konsist-test:test` in CI
- **[review]**: maintainer review, and agents' own review before opening a PR; the mechanical ones move to Konsist in follow-up work

When a rule and nearby code disagree, follow the rule. Fix the nearby code only if your change already touches it.

## 1. Formatting and files

- ktlint with the Compose rules decides formatting. Run `./gradlew spotlessApply` before pushing; nothing formats on compile. **[Spotless]**
- No wildcard imports, including `res.*`. ktlint's `no-wildcard-imports` is still disabled in `.editorconfig`. **[review]**
- One top-level public type per file, named after it. **[review]**
- Production files stay under 800 lines. Today's larger files are on an allowlist that can only shrink. **[Konsist]**

## 2. Comments

- No KDoc, no comments that restate the code. **[review]**
- Comment only a non-obvious invariant, tricky concurrency or a workaround, in one or two lines. Good: [AppsRepositoryImpl.kt:300-301](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/apps/data/src/commonMain/kotlin/zed/rainxch/apps/data/repository/AppsRepositoryImpl.kt#L300-L301), [BackgroundRequest.kt:13-14](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/data/src/commonMain/kotlin/zed/rainxch/core/data/network/BackgroundRequest.kt#L13-L14).
- No em-dashes anywhere: code, strings, logs, comments. **[review]**

## 3. Modules and packages

- Packages are `zed.rainxch.<feature>.<layer>`, e.g. `zed.rainxch.devprofile.data` (hyphens dropped). **[Konsist]**
- Common domain code imports no Ktor, Room, Compose, lifecycle, Koin or Android types. Platform `actual`s may. **[Konsist]**
- Presentation never imports a data package. **[Konsist]**
- Repository interfaces live in domain, `XRepositoryImpl` in data. **[review]**
- No `java.*` or `android.*` in `commonMain`. Bad: [AppsViewModel.kt:74](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/apps/presentation/src/commonMain/kotlin/zed/rainxch/apps/presentation/AppsViewModel.kt#L74). **[review]**

## 4. ViewModels

The recommended shape, already used by 11 ViewModels ([AppsViewModel.kt:113-128](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/apps/presentation/src/commonMain/kotlin/zed/rainxch/apps/presentation/AppsViewModel.kt#L113-L128), [StarredReposViewModel.kt:39-55](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/starred/presentation/src/commonMain/kotlin/zed/rainxch/starred/presentation/StarredReposViewModel.kt#L39-L55)):

```kotlin
class FooViewModel(
    private val repository: FooRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(FooState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    load()
                    hasLoadedInitialData = true
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), FooState())

    private val _events = Channel<FooEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: FooAction) {
        when (action) {
            FooAction.OnRefresh -> load()
        }
    }
}
```

- `FooAction` and `FooEvent` are sealed. **[Konsist]**
- One public input: `onAction`. No other public functions. **[review]**
- Update state with `_state.update { }`, never `_state.value = _state.value.copy(...)`. **[review]**
- Combine flows with `combine`, not several collectors writing shadow `var`s. Bad: [HomeViewModel.kt:64-86](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/home/presentation/src/commonMain/kotlin/zed/rainxch/home/presentation/HomeViewModel.kt#L64-L86). **[review]**
- Route arguments come from the route object or one typed params class, never positional `parametersOf` (*A8*). Bad: [ViewModelsModule.kt:47-52](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/composeApp/src/commonMain/kotlin/zed/rainxch/githubstore/app/di/ViewModelsModule.kt#L47-L52). **[review]**

## 5. State

- A `data class FooState` with defaults, `ImmutableList` / `ImmutableSet` for collections. **[review]**
- No `@Stable` / `@Immutable` on screen state; keep `@Stable` for real state holders like [KomiToastState.kt:8](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/presentation/src/commonMain/kotlin/zed/rainxch/core/presentation/components/overlays/KomiToastState.kt#L8). **[review]**
- Derive values in one place before the UI sees them. Good: [RawDetailsStateMapper.kt:11](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/details/presentation/src/commonMain/kotlin/zed/rainxch/details/presentation/RawDetailsStateMapper.kt#L11). **[review]**
- No sentinel strings or nullable fields used as flags. Bad: [AppsViewModel.kt:880](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/apps/presentation/src/commonMain/kotlin/zed/rainxch/apps/presentation/AppsViewModel.kt#L880). **[review]**
- Errors in state are typed, not `String`. **[review]**

## 6. Errors and cancellation

- Never wrap suspend calls in `runCatching`; it swallows `CancellationException`. **[review]**
- A broad `catch` around suspend code rethrows `CancellationException` first. Good: [DetailsViewModel.kt:208-212](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/details/presentation/src/commonMain/kotlin/zed/rainxch/details/presentation/DetailsViewModel.kt#L208-L212). **[review]**
- `Throwable.message` never reaches the user. Map typed errors to resources at render time. Good: the typed cases in [MirrorPickerRoot.kt:240-245](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/tweaks/presentation/src/commonMain/kotlin/zed/rainxch/tweaks/presentation/mirror/MirrorPickerRoot.kt#L240-L245) (its fallback case on the next line still shows exception text). Bad: [DetailsViewModel.kt:3009](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/details/presentation/src/commonMain/kotlin/zed/rainxch/details/presentation/DetailsViewModel.kt#L3009). **[review]**
- Fallible repository calls return `Result<T>` with typed domain exceptions. A typed outcome to copy: [ProxyTestOutcome.kt:3-27](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/domain/src/commonMain/kotlin/zed/rainxch/core/domain/network/ProxyTestOutcome.kt#L3-L27).
- Log with the injected `KomiStoreLogger` and pass the throwable, never only `.message`. **[review]**

## 7. Navigation

- Routes are `@Serializable` members of `GithubStoreGraph` ([GithubStoreGraph.kt:5](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/composeApp/src/commonMain/kotlin/zed/rainxch/githubstore/app/navigation/GithubStoreGraph.kt#L5)). **[review]**
- A click that only navigates is a lambda on the Screen (`onNavigateToDetails`), not an Action. **[review]**
- The ViewModel sends a `Navigate*` event only when navigation depends on async work. **[review]**
- No empty `-> Unit` branches for navigation in `onAction`. Bad: [HomeViewModel.kt:212](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/home/presentation/src/commonMain/kotlin/zed/rainxch/home/presentation/HomeViewModel.kt#L212). **[review]**

## 8. Composables

- A public `FooRoot(callbacks, viewModel = koinViewModel())` collects with `collectAsStateWithLifecycle`, handles events with `ObserveAsEvents`, and calls a private stateless `FooScreen(state, onAction, modifier)`. Closest today: [ImportStarsRoot.kt:64-88](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/favourites/presentation/src/commonMain/kotlin/zed/rainxch/favourites/presentation/import/ImportStarsRoot.kt#L64-L88), which still lacks the `modifier` parameter, as every route-level screen does. **[review]**
- Every composable that emits UI takes `modifier: Modifier = Modifier` as its first optional parameter. **[Spotless]**
- Pass leaf components the values they need, not the whole screen state. Bad: [SmartInstallButton.kt:91-99](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/details/presentation/src/commonMain/kotlin/zed/rainxch/details/presentation/components/SmartInstallButton.kt#L91-L99). **[review]**
- No business logic, `koinInject`, or dispatcher switching inside composables. Bad: [About.kt:98-100](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/details/presentation/src/commonMain/kotlin/zed/rainxch/details/presentation/components/sections/About.kt#L98-L100), [About.kt:140-149](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/details/presentation/src/commonMain/kotlin/zed/rainxch/details/presentation/components/sections/About.kt#L140-L149). **[review]**
- Lazy list items have stable keys. **[review]**
- Anything named `rememberX` calls `remember`. Bad: [MarkdownUtils.kt:17](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/presentation/src/commonMain/kotlin/zed/rainxch/core/presentation/components/markdown/MarkdownUtils.kt#L17). **[review]**

## 9. Design system

- Use `Komi*` components and `LocalPersonality.current` tokens. No `MaterialTheme.*`, no `Color(0x...)` literals, no `isSystemInDarkTheme()`. Bad: [DetailsRoot.kt:305](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/details/presentation/src/commonMain/kotlin/zed/rainxch/details/presentation/DetailsRoot.kt#L305), [ExpandableDetails.kt:89](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/presentation/src/commonMain/kotlin/zed/rainxch/core/presentation/components/markdown/ExpandableDetails.kt#L89). **[review]**
- Before writing a component, look in core/presentation. Shared needs (empty and error states, favourite toggle, search bar, sort dropdown) belong there, not in a feature. **[review]**
- New core components dispatch on the personality with private Manga / Classic implementations, like [KomiButton.kt:70-123](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/presentation/src/commonMain/kotlin/zed/rainxch/core/presentation/components/buttons/KomiButton.kt#L70-L123). **[review]**
- Public core components and stateless screens have a preview wrapped in `PersonalityPreview` ([PersonalityPreview.kt:16](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/presentation/src/commonMain/kotlin/zed/rainxch/core/presentation/personality/utils/PersonalityPreview.kt#L16)). **[review]**

- Text goes through `KomiText(role = ...)`. The role sets size, weight, letter spacing and case; `KomiText` has no parameters to override them. Seven sizes: 11, 12, 14, 16, 20, 24, 32. **[Konsist]**

  | Role | Size | Use it for |
  |---|---|---|
  | `Display` | 32 | Hero names, onboarding headlines |
  | `ScreenTitle` | 24 | Top bar titles |
  | `SectionTitle` | 20 | Page sections, empty-state titles |
  | `CardTitle` | 16 | Card, sheet and dialog titles |
  | `ItemTitle` | 16 | List rows, picker options |
  | `Body` | 14 | Running text, row subtitles |
  | `Reading` | 16 | Long-form text: release notes, descriptions |
  | `Caption` | 12 | Meta: counts, dates, sizes |
  | `Mono` | 12 | Versions, hashes, package names, logs |
  | `LabelLarge`, `Label`, `LabelSmall` | 16, 14, 12 | Controls by size: large, medium and small buttons, chips, navigation |
  | `Overline` | 11 | Kickers above a field or group, always in capitals |
  | `Stamp` | 12 to 14 | Segmented tabs, badges, Manga stamps |
  | `Numeral` | 24 | Stat values |

- Case comes from the role. Manga sets `Display`, `ScreenTitle`, `SectionTitle` and `Stamp` in capitals; everything else keeps the text's own case. A selected row shows its state with color or a check, not a heavier weight. **[review]**
- Large text that must keep its own case or face (the repository name in Details, the device sign-in code) uses `BasicText` with a personality style resized from another role, such as `type.display` or `type.mono.copy(fontSize = type.screenTitle.fontSize)`. Never a literal size. **[Konsist]**
- Tints come from `PersonalityColors`: `primaryTint`, `onPrimaryTint`, `errorTint`, `pressOverlay`, `disabledContent`, `disabledContainer`, `scrimOverlay` and `divider`. No `.copy(alpha = 0.x)` on personality colors. **[Konsist]**
- Motion comes from `LocalPersonality.current.motion`: `fastSpatial()` and `defaultSpatial()` for movement, `bouncySpatial()` for a press or swap that should feel alive, `fastEffects()`, `defaultEffects()` and `slowEffects()` for fades and color, `short()`, `medium()` and `long()` durations, `ambient()` for loops. Stop a loop when `motion.animates` is false. **[Konsist]**
- A screen with a back action passes `onBack` to `KomiTopBar`, not an arrow in `leading`. In Classic the top bar hands `onBack`, `actions` and the scaffold's FAB to a `KomiActionBar` floating over the content at the bottom (a cookie back button, the actions in a floating toolbar) and keeps only the title; both slide away while the content scrolls down. Manga draws them in the top bar. Pass `actions = null` when a screen has none to show, not an empty lambda. **[review]**
- Classic tab screens have no top bar: their `KomiScaffold` draws a tab card at the bottom (a search field, the screen's actions and FAB, and the tab row, which folds away while the content scrolls down). The app shell gives each tab destination a `LocalTabCardSlot` and the tabs; the screen only uses `KomiTopBar` as usual. A tab with its own search field (Library's filter) passes it as `search`; on the others the card's field opens Search, which is not a tab. Its list scrolls under the card: pad the container with `innerPadding.withoutBottom()` and add `innerPadding.calculateBottomPadding()` to the list's content padding. **[review]**
- Popups go through core components: `KomiTooltip`, `KomiRichTooltip` and `KomiCoachmark` for tips, `KomiDropdown` for menus, `KomiMenuList` for a sheet that lists options (a picker), `KomiSheet` for sheets. **[Konsist]**

## 10. Strings and accessibility

- All user-visible text comes from `Res.string`, content descriptions included. Bad: [KomiChip.kt:255](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/presentation/src/commonMain/kotlin/zed/rainxch/core/presentation/components/chips/KomiChip.kt#L255). **[review]**
- Counts use `<plurals>`, not "(s)". **[review]**
- Toggles and selectable rows set `Role` and `stateDescription`; error states announce themselves with `liveRegion`. **[review]**
- New keys go into the default `strings.xml` with a feature prefix (`tweaks_...`). **[review]**

## 11. Data layer and coroutines

- DTOs live in `dto/`; mappers live in `mappers/` as `toDomain()` extensions. **[review]**
- Use the injected HTTP clients. No `HttpClient {}` in feature code: those skip the proxy and are never closed. Bad: [FeedRepositoryImpl.kt:41](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/feature/feed/data/src/commonMain/kotlin/zed/rainxch/feed/data/repository/FeedRepositoryImpl.kt#L41). **[review]**
- Long-lived work runs on the injected app scope, not a private `CoroutineScope` in a singleton. **[review]**
- No `runBlocking` outside app startup. **[review]**

## 12. DI

- Data modules declare `single<FooRepository> { FooRepositoryImpl(named = get()) }`. **[review]**
- ViewModels use `viewModelOf(::FooViewModel)` unless they take route arguments. **[review]**
- No stateful `object` singletons outside DI, and no `named("test")` for production clients. Bad: [ProxyManager.kt:23](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/data/src/commonMain/kotlin/zed/rainxch/core/data/network/ProxyManager.kt#L23). **[review]**

## 13. Tests

- `kotlin.test`, hand-written fakes, no mocking library. **[review]**
- Test names are snake_case sentences, like [UpdateVerdictTest.kt:62](https://github.com/komi-store/komi-store/blob/cfa6bd30e6b35cbbc369d78a4599182a9a66beac/core/domain/src/commonTest/kotlin/zed/rainxch/core/domain/utils/UpdateVerdictTest.kt#L62). **[review]**
- Shared fakes live in one place per module instead of one copy per test file. **[review]**
- New ViewModels come with tests once `kotlinx-coroutines-test` and Turbine are in the version catalog (follow-up).

## 14. Docs

- CLAUDE.md files state only what the code can't tell you in a minute. **[review]**
- Change a CLAUDE.md in the same PR as the code it describes. **[review]**
