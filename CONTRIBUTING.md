# Contributing to Komi Store

Thanks for your interest in helping out. This document covers everything you need to ship a change — from setting up the project locally to landing a clean PR.

If you're contributing to a sibling repo ([`backend`](https://github.com/komi-store/komi-store-backend) or [`api`](https://github.com/komi-store/komi-store-backend-data)), check that repo's own contributing notes; the rules below are specific to the **Komi Store** client app.

---

## Table of contents

- [Code of conduct](#code-of-conduct)
- [Ways to contribute](#ways-to-contribute)
- [Reporting bugs](#reporting-bugs)
- [Suggesting features](#suggesting-features)
- [Local development setup](#local-development-setup)
- [Project layout](#project-layout)
- [Coding conventions](#coding-conventions)
- [Branching and commit style](#branching-and-commit-style)
- [Using AI tools](#using-ai-tools)
- [Pull request process](#pull-request-process)
- [Translations](#translations)
- [Release flow](#release-flow)
- [Security disclosures](#security-disclosures)
- [Questions](#questions)

---

## Code of conduct

Everyone interacting with this project — issues, pull requests, Discord, anywhere — is expected to follow our [Code of Conduct](CODE_OF_CONDUCT.md) (Contributor Covenant). Short version: be respectful, assume good intent, disagree about ideas — never about people. Maintainers will enforce it.

---

## Ways to contribute

Plenty of ways to help, even without writing code:

- **Report bugs.** Reproducible reports with logs are gold.
- **Suggest features.** Open an issue describing the user-facing problem first; the implementation can be discussed there.
- **Triage issues.** Reproduce open bugs, ask for missing info, label them.
- **Write code.** Pick up a [`good first issue`](https://github.com/komi-store/komi-store/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) or any [`help wanted`](https://github.com/komi-store/komi-store/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22) issue.
- **Translate strings.** See [Translations](#translations).
- **Test pre-releases.** Watch the repo for new tags and try them out.

---

## Reporting bugs

Two ways:

- **Inside the app: `Tweaks → Send feedback`.** Auto-fills app version, platform, and installer. Pick the channel (email or GitHub issue) and you're done.
- **On GitHub: [open a bug report](https://github.com/komi-store/komi-store/issues/new?template=bug_report.md).** Tell us what went wrong, how to reproduce, your setup. Logs and screenshots help but aren't required.

If you have logs handy, grab them from:

- **Android:** `adb logcat | grep zed.rainxch.githubstore`
- **Desktop:** `~/Library/Logs/Komi-Store/session.log` (macOS), `%LOCALAPPDATA%/Komi-Store/logs/session.log` (Windows), `$XDG_STATE_HOME/Komi-Store/logs/session.log` (Linux). Crashes also drop a `crash-<timestamp>.log` next to it.

---

## Suggesting features

[Open a feature request](https://github.com/komi-store/komi-store/issues/new?template=feature_request.md). Lead with the **pain**, not the solution: what are you trying to do, where does the app fall short?

For bigger ideas (new screens, new platform, architectural shifts), open the issue first and wait for a quick discussion before writing code.

---

## Local development setup

### Requirements

- **JDK 21** (Temurin recommended). Set `JAVA_HOME` accordingly.
- **Android Studio** Narwhal 3 Feature Drop (2025.1.3) or newer, which AGP 8.13 needs, with the Kotlin Multiplatform plugin.
- **Android SDK** for Android builds (target API 36, min API 26).
- **Git** with a configured username/email.

### One-time setup

```bash
git clone https://github.com/komi-store/komi-store.git
cd komi-store
```

Create `local.properties` in the repo root:

```properties
sdk.dir=/path/to/Android/sdk
```

`GITHUB_CLIENT_ID` is optional. Without the key the build uses the app's built-in OAuth client id; add `GITHUB_CLIENT_ID=<id>` only to sign in through your own OAuth app. Don't leave it empty: an empty value overrides the built-in id and breaks sign-in.

### Common commands

```bash
# Android debug build
./gradlew :androidApp:assembleDebug

# Android release build (unsigned; the build has no signing config)
./gradlew :androidApp:assembleRelease

# Desktop run (dev mode)
./gradlew :composeApp:run

# Desktop installers
./gradlew :composeApp:packageExe :composeApp:packageMsi    # Windows
./gradlew :composeApp:packageDmg :composeApp:packagePkg    # macOS
./gradlew :composeApp:packageDeb :composeApp:packageRpm    # Linux

# Full build check (compile + lint)
./gradlew build

# Format check (ktlint through Spotless, files changed against main)
./gradlew spotlessCheck

# Auto-format the same files
./gradlew spotlessApply
```

The first build will pull a lot of dependencies. Subsequent builds reuse the Gradle build cache (enabled in `gradle.properties`).

---

## Project layout

```text
androidApp/                          # Android application: activity, manifest, resources, R8 rules
composeApp/                          # Shared app code, navigation, DI wiring, Desktop entry point
core/
  domain/                            # Interfaces, models, pure logic
  data/                              # Repositories, Ktor, Room, Koin, platform code
  presentation/                      # Personality design system, Komi* components, strings
feature/
  apps/                              # Installed apps, updates, import
  auth/                              # Sign-in (web OAuth, device flow, personal access token)
  details/                           # Repository and release details, installs
  dev-profile/                       # Developer profile
  favourites/                        # Saved favourites, importing stars
  feed/                              # Explore feed
  home/                              # Top charts
  profile/                           # Account, announcements, what's new
  recently-viewed/                   # Recently viewed repositories
  repo-pages/                        # Issues, security advisories
  search/                            # Search with filters
  starred/                           # Starred repositories
  tweaks/                            # Settings
konsist-test/                        # Architecture rules
build-logic/convention/              # Gradle convention plugins
```

Each `feature/<name>/` has up to three sub-modules: `domain/`, `data/`, `presentation/`. `profile`, `recently-viewed` and `tweaks` are presentation-only.

The architecture is described in [`docs/agents/architecture.md`](docs/agents/architecture.md). Per-feature notes live in `feature/<name>/CLAUDE.md`.

---

## Coding conventions

We use Kotlin's official style, enforced by Spotless (ktlint plus the Compose rules) on the files you change. The full rule set, with examples from the codebase, is [`docs/code-style.md`](docs/code-style.md). In short:

- **Clean Architecture with MVVM.** The domain layer has no framework dependencies, the data layer implements it, and presentation holds ViewModels and Compose UI.
- **Every screen** has a ViewModel with one `state` flow, one `onAction(FooAction)` taking a sealed action type, and a `Channel` for one-off events, plus a `FooRoot` composable that collects them and calls a stateless `FooScreen`.
- **Errors are typed** and become string resources in the UI. Never show an exception's message to users.
- **UI uses `Komi*` components** and `LocalPersonality` tokens; all user-visible text comes from string resources.
- **No KDoc or filler comments.** Comment only non-obvious invariants, tricky concurrency or workarounds.
- **Dependencies** go into `gradle/libs.versions.toml`, and new modules use a convention plugin from `build-logic/convention/` instead of a hand-rolled build script.

The whole codebase was formatted once in #1080. That commit is listed in `.git-blame-ignore-revs`, which GitHub's blame view already skips; run `git config blame.ignoreRevsFile .git-blame-ignore-revs` once so local `git blame` skips it too.

---

## Branching and commit style

### Branches

- Default branch: `main`.
- **Never** commit directly to `main`. Open a feature branch first.
- Naming convention (matches what's already in the repo):
  - `feat/<issue#>-<short-slug>` for features.
  - `fix/<short-slug>` for bug fixes.
  - `chore/<short-slug>` for refactors, dep bumps, docs, build cleanup.
  - Examples: `feat/470-sui-support`, `fix/auth-stuck-on-direct-and-dialog-loop`, `chore/drop-legacy-query-hash`.

### Commits

- One commit = one logical change. Don't bundle a refactor with a feature, or two unrelated features in the same commit. When asked to commit a batch of work, split it into the smallest meaningful commits.
- Commit messages are short and imperative. Match the repo's existing style — usually a single sentence under 72 characters:
  - ✅ `fix: clear parked install metadata once the system confirms install`
  - ✅ `feat: support multi-select platform filter on Home`
  - ❌ `fixed bug` / `wip` / `more changes`
- Long bodies are fine for non-trivial changes — explain the *why*, not the *what* (the diff already shows the *what*).

---

## Using AI tools

AI coding tools are welcome. These keep such PRs easy to review:

- **You own every line.** Read the diff before you push and be ready to explain any part of it.
- **Point your tool at the repo's guidance.** [`AGENTS.md`](AGENTS.md) is written for coding agents (Claude Code reads it through `CLAUDE.md`), and `feature/<name>/CLAUDE.md` holds notes per feature.
- **Run the checks and try the change.** CI only catches what it can check; say in the PR which device or OS you ran it on.
- **Keep it small and in scope.** One issue, one PR. Large generated refactors are hard to review; open an issue first.
- **Write the description yourself, briefly:** what changed, why, how you tested it.
- **Trim generated trailers.** `Co-Authored-By: Claude/Cursor/...` lines add noise to `git log`; remove them before pushing.

---

## Pull request process

1. **Open or claim an issue** before doing anything substantial. For tiny fixes (typos, one-liners) you can skip this step.
2. **Branch from latest `main`:**

   ```bash
   git fetch origin
   git checkout -b feat/123-some-thing origin/main
   ```

3. **Make the change.** Keep the PR focused; if it grows beyond one logical change, split it into a stack.
4. **Run the checks CI runs.** CI (`.github/workflows/pr-checks.yml`) builds and tests every PR and runs Spotless, detekt, Android Lint, the Compose stability check and Konsist. The commands are under "Before you push" in [`AGENTS.md`](AGENTS.md) (`/verify` in Claude Code); run them before opening the PR instead of waiting for CI.
5. **Push and open a PR** against `main`. The PR template asks:
   - **What and why:** what changes for the user or the codebase, with the issue linked (`Fixes #123`).
   - **How it was tested:** the device or OS, the steps and what you saw, with screenshots or a recording for UI changes.
   - **Notes for the reviewer:** anything you're unsure about.
6. **Address review comments** with follow-up commits (don't force-push during review unless the reviewer asks).
7. **Merge** is performed by maintainers via GitHub's "Squash and merge". Your branch is deleted automatically.

### What gets a PR rejected

- Mixed concerns (refactor + feature + dep bump in one PR).
- New dependencies without justification in the description.
- Visual changes without screenshots.
- Code that ignores the project's existing patterns ("why isn't this an `XViewModel`?", "why did you skip the convention plugin?").
- Empty / vague commit messages or "WIP" commits left in history.

---

## Translations

Komi Store ships in 14 languages. String resources live in:

```text
core/presentation/src/commonMain/composeResources/
  values/strings.xml             # Default (English)
  values-ar/strings-ar.xml       # Arabic
  values-bn/strings-bn.xml       # Bengali
  values-es/strings-es.xml       # Spanish
  values-fr/strings-fr.xml       # French
  values-hi/strings-hi.xml       # Hindi
  values-it/strings-it.xml       # Italian
  values-ja/strings-ja.xml       # Japanese
  values-ko/strings-ko.xml       # Korean
  values-pl/strings-pl.xml       # Polish
  values-ru/strings-ru.xml       # Russian
  values-tr/strings-tr.xml       # Turkish
  values-zh-rCN/strings-zh-rCN.xml  # Chinese (Simplified)
  values-zh-rTW/strings-zh-rTW.xml  # Chinese (Traditional)
```

To add a translation:

1. Find the missing or new key in `values/strings.xml`.
2. Add the localized version to the matching `values-<lang>/strings-<lang>.xml`.
3. Open a PR titled `i18n: <language> — <short note>` (e.g., `i18n: Spanish — translate APK Inspect strings`).

If you're adding a brand-new locale, ping a maintainer first so we can wire up the locale file and any locale-specific resources.

Don't machine-translate without verification — bad translations are worse than missing ones because they push out fallback English.

---

## Release flow

Releases are cut by maintainers from `main` after a short stabilization window:

1. Bump `projectVersionName` and `projectVersionCode` in `gradle/libs.versions.toml`.
2. Tag the commit `vX.Y.Z`.
3. The maintainer builds and signs Android. Desktop installers (`.exe`, `.msi`, `.dmg`, `.pkg`, `.deb`, `.rpm`, `.appimage`, `.tar.zst`) are built by CI from the `generate-installers` branch, which also creates the draft release (see [`docs/agents/distribution.md`](docs/agents/distribution.md)).
4. Release notes are written manually — covering the user-facing changes, not every commit.
5. F-Droid and the in-app updater pick up the release automatically.

You don't normally need to touch this as a contributor; just be aware that landing a feature in `main` doesn't immediately ship — there's a release cadence on top.

---

## Security disclosures

**Do not open public issues for security vulnerabilities.** Use [GitHub's private vulnerability reporting](https://github.com/komi-store/komi-store/security/advisories/new) or the contact in [`SECURITY.md`](SECURITY.md).

We treat token leaks, install-flow exploits, and signing-bypass paths as critical. Other issues we'll triage on a best-effort basis.

---

## Questions

- **Real-time chat:** join the [Discord server](https://discord.komistore.app) — fastest way to get a hand from maintainers and other contributors.
- **Email:** `admin@komistore.app` for anything that doesn't fit a public channel — sponsorship, partnerships, sensitive coordination.
- **General questions / discussion:** open a [GitHub Discussion](https://github.com/komi-store/komi-store/discussions) (if enabled) or a feature-request issue.
- **Stuck on local setup:** open a draft PR with what you have and ask for help in the description — we'd rather help you finish than have you give up silently.
- **Sibling repos:** [backend](https://github.com/komi-store/komi-store-backend) and [api](https://github.com/komi-store/komi-store-backend-data) have their own contributing notes.

Thanks for making Komi Store better.
