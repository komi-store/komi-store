# Checks

CI (`.github/workflows/pr-checks.yml`) runs on every pull request and every push to `main`. Existing findings live in committed baselines, so a check fails only on new issues.

```bash
./gradlew spotlessApply                                   # format files changed vs origin/main (ktlint 1.8.0 + Compose rules + .editorconfig)
./gradlew detekt                                          # static analysis; baseline: <module>/detekt-baseline.xml
./gradlew lintDebug                                       # Android Lint; baseline: <module>/lint-baseline.xml
./gradlew jvmTest koverXmlReport                          # tests and merged coverage (build/reports/kover)
./gradlew compileDebugKotlinAndroid; ./gradlew debugStabilityCheck   # Compose stability vs <module>/stability/*.stability
./gradlew :konsist-test:test                              # architecture rules
./gradlew spotlessInstallGitPrePushHook                   # opt-in pre-push format check
```

CodeQL (`codeql.yml`) and Dependabot (`dependabot.yml`) run separately.

## Spotless

Spotless checks only files changed against `origin/main`, but it checks the whole file. The codebase was formatted once in #1080 (listed in `.git-blame-ignore-revs`). Compose rule findings (missing `modifier`, parameter order, naming) are not auto-fixable and have to be fixed by hand in files you touch.

## Baselines

ktlint findings the formatter can't fix (mostly Compose rules) are listed per file and rule in `config/ktlint/baseline.txt`, and Spotless suppresses exactly those. When you fix one, delete its line.

Refresh a baseline only for an intentional change: `detektBaseline`, `updateLintBaseline` or `debugStabilityDump`. The stability analyzer (0.7.0) shares one output file across a module's compilations, so run its tasks in their own Gradle invocation right after `compileDebugKotlinAndroid` (fixed upstream only in releases built for Kotlin 2.4). Kotlin must match the stability analyzer's Kotlin version (see its version map) when bumping either.

## Warnings as errors

Kotlin warnings are errors (`allWarningsAsErrors`). A module can opt out with `komi.warningsAsErrors=false` in its own `gradle.properties`; none does.

## Konsist

The rules and their allowlists live in `konsist-test/src/test/kotlin/zed/rainxch/konsist/`. An allowlist can only shrink: the file-size test fails when an allowlisted file drops under the limit, so remove the entry in the same PR.

`DocsRulesTest` reads `AGENTS.md`, `CLAUDE.md`, `CONTRIBUTING.md`, `docs/agents/*.md` and `feature/*/CLAUDE.md`. A backticked route such as `GithubStoreGraph.DetailsScreen` must exist in the graph, and any backticked name ending in Screen or Graph must still exist in production code. When you rename a route or screen, update the docs in the same PR.

## Agent config

The `Agent config` job runs [agnix](https://github.com/agent-sh/agnix) over the repository (`AGENTS.md`, the `CLAUDE.md` files, `.claude/` and the rest) with its default rules, and fails only on errors. It runs in CI only.
