# Auth Feature

Three GitHub sign-in paths, picked per session: **web OAuth + PKCE + handoff** (primary), **device flow** (fallback), **Personal Access Token paste** (last resort). Works on Android + Desktop.

## Structure

```text
feature/auth/
  domain/repository/   # AuthenticationRepository + AuthPath, DeviceFlowStart, PollOutcome, DevicePollResult, WebAuthRegistration, AuthException, PatRejectedException / RejectedKind
  data/
    crypto/PkceGenerator.kt                     # SHA-256 PKCE triplet (state + verifier + challenge)
    network/GitHubAuthApi.kt                    # direct + backend-proxied device-flow endpoints, PAT validation
    network/WebAuthApi.kt                       # web-OAuth register + handoff consume
    repository/AuthenticationRepositoryImpl.kt
    repository/AuthErrors.kt                    # toAuthException(), infra-error checks behind the Direct fallback
    di
  presentation/
    AuthenticationViewModel / State / Action / Events / Root
    AuthDeepLinkBus.kt                          # SharedFlow bridge from app-level deep-link parser to VM
```

`AuthenticationRepository.awaitDeviceToken` has no callers; the VM polls with `pollDeviceTokenOnce`.

## Navigation

`GithubStoreGraph.AuthenticationScreen`.

## Web OAuth path (primary)

1. `PkceGenerator.generate()` -> `(state, codeVerifier, codeChallenge)` (S256).
2. `WebAuthApi.register(state, codeChallenge, codeVerifier)` -> POST `https://github-store.org/auth/register`. Cloudflare Worker stores `(state -> {codeVerifier, codeChallenge})` in Workers KV with short TTL and returns `auth_url` (the `github.com/login/oauth/authorize?...` URL).
3. VM saves `state` in `SavedStateHandle` and opens `auth_url` in user's browser via `BrowserHelper`. A watchdog errors out if no callback arrives in 11 minutes.
4. User authorizes on GitHub. GitHub redirects to `https://github-store.org/auth/callback?code=...&state=...`.
5. Worker re-validates `state`, exchanges `code` against `api.github-store.org` (backend posts to GitHub `/oauth/access_token` with the stored verifier), backend writes `(handoffId -> access_token)` to Postgres with 60s TTL.
6. Worker redirects browser to `githubstore://auth?handoff=<handoffId>&state=<state>` (or `?error=<reason>&state=<state>`), picked up by the app via OS deep link.
7. App-level `DeepLinkParser` result is published to `AuthDeepLinkBus` and the app navigates to `AuthenticationScreen`. VM calls `exchangeWebAuthHandoff(handoffId)` -> `WebAuthApi.consumeHandoff` -> POST `api.github-store.org/v1/oauth/handoff/<id>` (atomic `DELETE...RETURNING`, single-use). Token lands in `TokenStore`.

Custom scheme is public: any app can fire `githubstore://auth?...`. The VM exchanges a handoff only when a web-auth session is pending and its `state` matches the one saved in step 3; server side the handoff is single-use with a 60s TTL.

## Device flow path (fallback)

User picks it ("Use device code instead") when the web flow can't complete (browser unreachable, deep link not registered).

- Primary sub-path: backend proxy `/v1/auth/device/start` + `/poll` on `api.github-store.org` for networks throttling `github.com` (China, corporate filters).
- Each session picks one `AuthPath` (`Backend` | `Direct`), persists in `SavedStateHandle`. Start falls back to `Direct` on infra errors (timeout / connect failure / backend 5xx / network-error text) or a backend 429; a poll escalates `Backend -> Direct` only on infra errors. Other HTTP 4xx and GitHub negative 200-bodies (`authorization_pending`, `slow_down`, `access_denied`, `expired_token`, `bad_verification_code`) are real answers and never trigger fallback.
- Backend rate limits hard: 10 starts/hr, 200 polls/hr per IP. `GitHubAuthApi.http` (also used by `WebAuthApi`) has `HttpRequestRetry` with 2 retries on 5xx / exceptions. Only `startDeviceFlowDirect` adds `withRetry(maxAttempts = 3)` on top; don't add more retry loops.
- Backend responses carry `X-Request-ID`; `GitHubAuthApi` embeds it in error messages via `asRequestIdTag()` for cross-log correlation.

## PAT path (last resort)

`signInWithPat(token)`:
1. Client-side format check (rejects obvious paste-errors; needs `ghp_` / `github_pat_` prefix).
2. Network check against GitHub `/user`. 401 -> `PatRejectedException(BadCredentials)`, 403 -> `InsufficientScope`, other 4xx -> `Other(statusCode)`. On reject the sheet stays open.
3. If GitHub is unreachable (timeout/DNS/block, or a 5xx) the token persists optimistically; the whole reason this path exists is for users who can't reach GitHub reliably. A bad-but-unreachable token surfaces a 401 on the first authenticated call.

## Notes

- Errors: the repository fails with `AuthException` (`Unreachable` for I/O, timeouts, proxy failures and backend 5xx; `RateLimited` for a 429; `Denied`; `CodeExpired`; `Failed`) and `PatRejectedException` for the PAT path. The ViewModel keeps `AuthError` / `PatError` in state and the Root picks the string, so no exception text reaches the screen.
- Token stored via `TokenStore` (`DefaultTokenStore`: KSafe-encrypted, migrates the legacy DataStore entry once). Only `AuthenticationViewModel` collects `accessTokenFlow`, on the injected app `CoroutineScope`.
- `GitHubAuthApi` and `WebAuthApi` are Koin singles. Their clients come from `createPlatformHttpClient` with the `ProxyScope.DISCOVERY` proxy ("Search & metadata" in Tweaks) and are rebuilt when it changes, so every sign-in path follows the user's proxy.
- `GITHUB_CLIENT_ID` in `local.properties` for builds; must match the OAuth App registered on the backend and used by the Worker.
- Endpoints: `WEB_ORIGIN = https://github-store.org`, `BACKEND_ORIGIN = https://api.github-store.org`. Constants in `core/data/network/BackendEndpoints.kt`.
- Android deep-link filters in `androidApp/src/main/AndroidManifest.xml`: scheme `githubstore`, hosts `auth`, `callback`, `repo`, `apps`. Desktop registers the protocol via `DesktopDeepLink` on Win/Linux (macOS uses the bundle Info.plist `CFBundleURLTypes`).
