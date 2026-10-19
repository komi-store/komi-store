# Sign-in and the backend

## GitHub OAuth

`GITHUB_CLIENT_ID` comes from `local.properties`; without the key, the build uses the built-in client id (`BuildKonfigConventionPlugin`). An empty value overrides it and breaks sign-in. Deep links: `githubstore://auth` (web-OAuth handoff), `githubstore://callback` (leftover from the device flow), `githubstore://repo`, `githubstore://apps`; the full set is in `DeepLinkParser.kt`.

## Sign-in flow (web OAuth first)

1. **Web OAuth with PKCE and a handoff.** `feature/auth/data/crypto/PkceGenerator` mints `(state, codeVerifier, codeChallenge)`. `WebAuthApi.register` POSTs verifier, challenge and state to `https://github-store.org/auth/register` (a Cloudflare Worker stores them in Workers KV) and returns `authUrl`. The user authorizes on `github.com`, GitHub redirects to `github-store.org/auth/callback?code&state`, and the Worker exchanges the code via `api.github-store.org`. The backend stores `(handoffId -> access_token)` for 60 s in Postgres with an atomic `DELETE ... RETURNING`, then bounces back to `githubstore://auth?handoff=<handoffId>&state=<state>`. The app reads the handoff with `WebAuthApi.consumeHandoff` (GETDEL semantics).
2. **Device flow** through the backend's `/v1/auth/device/start` and `/poll`. `AuthPath` (`Backend` or `Direct`) is kept in `SavedStateHandle` and only escalates from `Backend` to `Direct` on infrastructure errors.
3. **Personal access token** pasted by the user (`signInWithPat`): validated against `/user`, and saved optimistically when GitHub is unreachable.

Backend rate limits: 10 device starts and 200 device polls per hour per IP. Endpoints are in `core/data/network/BackendEndpoints.kt` (`BACKEND_ORIGIN`, `WEB_ORIGIN`).

## The `X-GitHub-Token` header

The client attaches the user's token as `X-GitHub-Token` when `TokenStore.currentToken()` is non-null on `/v1/search`, `/v1/search/explore`, `/v1/repo` (including `POST .../refresh`), `/v1/releases`, `/v1/readme`, `/v1/user` and `/v1/users/{user}/repos`. The backend re-sends it as `Authorization: token $token` to GitHub; without it, the backend round-robins a pool of 4 service tokens.

- An upstream 401 comes back as a backend `502` and is treated like "GitHub unreachable": the client falls back through `shouldFallbackToGithubOrRethrow`.
- A `429` gets no fallback (the same limit applies), only backoff.
- `UnauthorizedInterceptor` sits only on the direct-GitHub client; `UserSessionRepositoryImpl` debounces consecutive 401s by token snapshot.
