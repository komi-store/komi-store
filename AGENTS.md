# Komi Store

Open-source app store for apps released on GitHub, Codeberg and other Forgejo forges. Kotlin Multiplatform and Compose Multiplatform: one codebase for Android (min API 26) and Desktop (Windows, macOS, Linux). Package `zed.rainxch.githubstore`.

## Map

- `composeApp/`: shared app code and the Desktop entry point: navigation (`GithubStoreGraph`, `AppNavigation`), DI wiring (`ViewModelsModule`, `initKoin`)
- `androidApp/`: the Android application: `MainActivity`, `GithubStoreApp`, manifest, resources, R8 rules
- `core/domain`: interfaces, models and pure logic, no framework imports
- `core/data`: repositories, Ktor clients, Room, Koin modules, Android and Desktop implementations
- `core/presentation`: the `Personality` design system, `Komi*` components, strings for 14 locales
- `feature/<name>/{domain,data,presentation}`: one folder per feature; some have only `presentation`
- `konsist-test/`: architecture rules; `build-logic/convention/`: convention plugins; versions in `gradle/libs.versions.toml`

## Build and run

```bash
./gradlew :androidApp:assembleDebug     # Android APK
./gradlew :composeApp:run               # Desktop
```

JDK 21+ and the Android SDK. Sign-in uses `GITHUB_CLIENT_ID` from `local.properties` when set.

## Before you push

CI runs these on every pull request. Existing findings live in committed baselines, so only new ones fail.

```bash
./gradlew spotlessApply                  # formats the files you changed
./gradlew compileKotlinJvm compileAndroidMain :androidApp:compileDebugKotlin jvmTest detekt :konsist-test:test
./gradlew lintDebug lintAndroidMain
./gradlew stabilityCheck                 # its own invocation, after compileKotlinJvm compileAndroidMain
```

In Claude Code, `/verify` runs the same steps. CI also lints agent docs with agnix. Details and baseline rules: `docs/agents/checks.md`.

## Rules the tools don't check

The full rule set, with examples, is `docs/code-style.md`. These apply to almost every change:

- A screen is a `FooRoot` that collects state and events, calling a stateless `FooScreen(state, onAction, modifier)`.
- A ViewModel exposes one `state: StateFlow`, takes one `onAction(FooAction)` with a sealed `FooAction`, and sends one-off effects through a `Channel`.
- Errors are typed and become string resources in the UI. Never show `Throwable.message`, and never wrap suspend calls in `runCatching`.
- Use `Komi*` components and `LocalPersonality` tokens, not `MaterialTheme` or color literals.
- All user-visible text, content descriptions included, comes from `Res.string`.
- No KDoc and no comments that restate the code; comment only non-obvious invariants and workarounds. No em-dashes.
- Don't guess APIs, versions or flags. Read the code or the docs first.

## Read when relevant

- `docs/agents/architecture.md`: layers, navigation, DI, core repositories, adding a feature
- `docs/agents/checks.md`: CI checks, baselines, warnings as errors, the stability analyzer
- `docs/agents/sign-in.md`: OAuth handoff, device flow, personal access tokens, the `X-GitHub-Token` header
- `docs/agents/forges.md`: Codeberg and Forgejo clients, per-host tokens, `RepoIdCodec`
- `docs/agents/releases-and-assets.md`: how releases, assets and installed apps are matched
- `docs/agents/platform.md`: silent installs, local network access, Desktop logs, encrypted storage, Gradle memory
- `docs/agents/translation.md`: translation providers
- `docs/agents/reviews.md`: ratings and reviews, the API contract with the backend
- `docs/agents/distribution.md`: installers, signing, Homebrew and WinGet (maintainers)
- `feature/<name>/CLAUDE.md`: notes for one feature
- `docs/quality/findings.md`: the research behind the rules
