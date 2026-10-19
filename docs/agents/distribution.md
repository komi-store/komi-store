# Distribution (maintainers)

Desktop installers are built by `.github/workflows/build-desktop-platforms.yml` on every push to the `generate-installers` branch. Its `release` job reads `projectVersionName` and creates the draft release `v<version>`.

## Windows signing (SignPath Foundation)

The `sign-windows` job runs after every push to `generate-installers`. The SignPath action is pinned to a commit SHA. Secrets: `SIGNPATH_API_TOKEN`, `SIGNPATH_ORGANIZATION_ID` (`1ecf111e-...`). The variable `SIGNPATH_SIGNING_POLICY_SLUG` is `test-signing` until the production certificate is issued, then `release-signing`. Project slug `GitHub-Store`, artifact configuration slug `initial`. The unsigned artifact is deleted after signing, so only `windows-installers-signed` reaches the draft release.

## WinGet

`.github/workflows/winget-publish.yml` fires on `release: [released]` and uses `vedantmgoyal9/winget-releaser` to publish the MSI (`installers-regex: '\.msi$'`) as `zed.rainxch.githubstore`. The secret `WINGET_TOKEN` is a PAT with `Contents` and `Pull requests` write access on `komi-store/winget-pkgs` (a fork of `microsoft/winget-pkgs`). The workflow doesn't set `fork-user`; the action defaults it to the repository owner, `komi-store`.

## Scoop

`.github/workflows/scoop-publish.yml` fires on release (or by hand) and dispatches `komi-store-release` to `komi-store/komi-store-scoop-bucket` with the secret `SCOOP_BUCKET_TOKEN`.

## macOS (Homebrew)

The cask lives in the tap `komi-store/komi-store` (repository `homebrew-komi-store`): `brew install --cask komi-store`. The app is unsigned for now, so users run `xattr -dr com.apple.quarantine /Applications/Komi-Store.app` after installing. CI builds `.dmg` and `.pkg` on every push to `generate-installers`. On release, `.github/workflows/homebrew-tap-publish.yml` updates `Casks/komi-store.rb` in the tap with the secret `HOMEBREW_TAP_TOKEN`; it can also be run by hand for a given tag.
