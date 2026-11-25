# Checks

CI (`.github/workflows/pr-checks.yml`) runs on every pull request and every push to `main`. There are no baselines: every check covers the whole codebase, and any finding fails it.

```bash
./gradlew spotlessApply                                   # format every Kotlin file and build script (ktlint 1.8.0 + Compose rules + .editorconfig)
./gradlew detekt                                          # static analysis; config/detekt/detekt.yml
./gradlew lintDebug lintAndroidMain                       # Android Lint; warnings fail it too
./gradlew jvmTest testAndroidHostTest koverXmlReport      # tests on the JVM and the Android host, merged coverage (build/reports/kover)
./gradlew compileKotlinJvm compileAndroidMain; ./gradlew stabilityCheck   # Compose stability vs <module>/stability/<project>.stability
./gradlew :konsist-test:test                              # architecture rules
./gradlew spotlessInstallGitPrePushHook                   # opt-in pre-push format check
```

CodeQL (`codeql.yml`) and Dependabot (`dependabot.yml`) run separately.

## Spotless

Spotless checks every Kotlin file and Gradle script. Compose rule findings (missing `modifier`, parameter order, naming) are not auto-fixable and have to be fixed by hand. A new `CompositionLocal` goes into `compose_allowed_composition_locals` in `.editorconfig`.

After editing `.editorconfig`, Spotless can keep reporting results from before the edit, because Gradle's build cache restores the `spotlessKotlin` lint output. To see the real result:

```bash
./gradlew --stop
rm -rf build/spotless-clean build/spotless-lints .gradle/configuration-cache
./gradlew spotlessApply --no-build-cache
```

## No baselines

detekt, Android Lint and ktlint have no baselines, and none may be added. Fix a finding in code; don't suppress it, ignore it in config or raise a threshold. The rules for the few allowed exceptions (third-party APIs that throw only a bare `RuntimeException`, min-SDK deprecations) are in `docs/code-style.md` section 6.

## Stability dumps

Refresh a module's stability dump (`stabilityDump`) only for an intentional change. The stability analyzer (0.15.0) merges what every compilation that ran wrote under `build/stability/`, so compile the JVM and Android main targets first and run its tasks in their own Gradle invocation. Kotlin must match the stability analyzer's Kotlin version (see its version map) when bumping either.

## Warnings as errors

Warnings are errors everywhere, with no opt-out: Kotlin in every module and in `build-logic` (`allWarningsAsErrors`), the Gradle Kotlin scripts (`org.gradle.kotlin.dsl.allWarningsAsErrors` in both `gradle.properties` files), Android Lint (`warningsAsErrors`) and detekt (any finding fails, and so does a config warning).

## Konsist

The rules and their allowlists live in `konsist-test/src/test/kotlin/zed/rainxch/konsist/`. An allowlist can only shrink: the file-size test fails when an allowlisted file drops under the limit, so remove the entry in the same PR.

`DocsRulesTest` reads `AGENTS.md`, `CLAUDE.md`, `CONTRIBUTING.md`, `docs/agents/*.md` and `feature/*/CLAUDE.md`. A backticked route such as `GithubStoreGraph.DetailsScreen` must exist in the graph, and any backticked name ending in Screen or Graph must still exist in production code. When you rename a route or screen, update the docs in the same PR.

`DesignTokenRulesTest` keeps design values in the personality package (`core/presentation/.../personality/`). Outside it, it bans raw `.sp` sizes, `FontWeight.`, hand-built `TextStyle(`, literal `tween` durations, literal `.copy(alpha = ...)` tints and `Color(0x...)`. Outside `core/presentation` it also bans Material's `Text`, `MaterialTheme.typography`, `Popup` and `TooltipBox`. Today's matches are counted per file in `konsist-test/src/test/resources/design-tokens/`: a file may not go over its count, and when it drops, lower or delete its line in the same PR.

## Agent config

The `Agent config` job runs [agnix](https://github.com/agent-sh/agnix) over the repository (`AGENTS.md`, the `CLAUDE.md` files, `.claude/` and the rest) with its default rules, and fails only on errors. It runs in CI only.
