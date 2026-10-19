# Distribution (maintainers)

Desktop installers are built by `.github/workflows/build-desktop-platforms.yml` on every push to the `generate-installers` branch.

## Windows signing (SignPath Foundation)

The `sign-windows` job runs after every push to `generate-installers`. The action is pinned to a commit SHA. Secrets: `SIGNPATH_API_TOKEN`, `SIGNPATH_ORGANIZATION_ID` (`1ecf111e-...`). The variable `SIGNPATH_SIGNING_POLICY_SLUG` is `test-signing` until the production certificate is issued, then `release-signing`. Project slug `GitHub-Store`, artifact configuration slug `initial`. The unsigned artifact is deleted after signing, so only `windows-installers-signed` reaches the draft release.

## WinGet

`.github/workflows/winget-publish.yml` fires on `release: [released]` and uses `vedantmgoyal9/winget-releaser`. The secret `WINGET_TOKEN` is a PAT with `Contents` and `Pull requests` write access on `komi-store/winget-pkgs` (a fork of `microsoft/winget-pkgs`). `fork-user: komi-store` is set explicitly, so the action doesn't infer the fork from the token owner.

## macOS (Homebrew)

The cask lives in the tap `komi-store/komi-store` (repository `homebrew-komi-store`): `brew install --cask komi-store`. The app is unsigned for now, so users run `xattr -dr com.apple.quarantine /Applications/Komi-Store.app` after installing. CI builds `.dmg` and `.pkg` on every push to `generate-installers`, and the tap's cask updates automatically on release.
