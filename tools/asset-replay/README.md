# Asset-matching replay corpus

`collect_corpus.py` fetches the newest releases of 48 real Android projects —
tag, publish date, pre-release flag, asset names — and writes them to

    core/domain/src/jvmTest/resources/asset-replay/corpus.tsv

One line per release: `repo<TAB>tag<TAB>published<TAB>prerelease<TAB>asset<TAB>asset…`.

`AssetMatchingReplayTest` (core/domain jvmTest) loads that file and replays the
update-check pick offline; it never touches the network.

Re-run the collector when the corpus should move forward:

    python3 tools/asset-replay/collect_corpus.py

Auth: `$GITHUB_TOKEN` / `$GH_TOKEN`, falling back to `gh auth token`.

## Ground rules of the replay

The test mirrors `InstalledAppsRepositoryImpl.resolveTrackedRelease` for a single
tracked app, on the release window `ReleaseWindow.kt` builds at its defaults
(newest first, pre-releases filtered). The device-only parts are pinned: an asset
is "installable" when its name ends in `.apk` (no arch filter), so every machine
replays the same data.

## What it guards

For every release that ships an .apk, the replay installs it and re-runs the
pick: an update must always be found, it may never point at an older release,
and the newest release must never lose its own match. The only fallbacks allowed
are the conservative ones — variant-locked (offline/online, debug/release) or
renamed/dual-brand projects — listed in the test.
