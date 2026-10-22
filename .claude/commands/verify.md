---
description: Run the checks CI runs on pull requests and report what fails
---

Run the checks that `.github/workflows/pr-checks.yml` runs, in this order, from the repository root. Use `--continue` so one failure doesn't hide the next.

1. `./gradlew spotlessApply` formats the files changed against `origin/main`. If it reports a lint it can't fix (Compose rules such as a missing `modifier` or parameter order), fix the code by hand.
2. `./gradlew compileKotlinJvm compileAndroidMain :androidApp:compileDebugKotlin jvmTest detekt :konsist-test:test --continue`
3. `./gradlew lintDebug lintAndroidMain --continue`
4. `./gradlew stabilityCheck` in its own invocation, after the JVM and Android compiles in step 2.

CI also runs an `Agent config` job (agnix); there is no local step for it.

Then report each step as passed or failed, with the first lines of each failure and the file and line it points at.

- Fix what this change caused. If a failure comes from code the change didn't touch, say so instead of fixing it.
- Don't refresh a baseline (`detektBaseline`, `updateLintBaseline` or `updateLintBaselineAndroidMain`, `stabilityDump`) unless the change is meant to alter it, and say so when you do.
- If Gradle can't start from your shell, run the same commands in a terminal and read the output from there.

Details: `docs/agents/checks.md`.
