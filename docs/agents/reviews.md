# Reviews

Ratings and written reviews for apps, stored by the backend (`komi-store-backend`). This file is the contract between the client and the backend: both sides build against it, and a change to it lands in both repos.

## Product rules

- A review belongs to a **repository**: GitHub repos by numeric repo id (so a rename keeps its reviews), Forgejo repos by host + owner + name. A multi-app repo shares one rating; each review can say which app it's about (`appLabel`).
- **Writing needs GitHub sign-in.** The client sends the user's token as `X-GitHub-Token`; the backend resolves it to a GitHub user. Reading needs nothing.
- **One review per GitHub user per repo.** Writing again edits it.
- **Anti-abuse:**
  - the GitHub account must be at least 30 days old (`REVIEW_MIN_ACCOUNT_AGE_DAYS`);
  - people with push access to the repo can't rate it;
  - writes are rate-limited per user and per IP;
  - a moderator can ban a user from reviewing.
- **A review has:**
  - a rating from 1 to 5;
  - optional text of up to 2000 characters (Unicode code points, so a client check of `String.length` never sends too much; blank text is stored as null);
  - the version reviewed (a release tag), the platform, and an optional app label.
- **Votes:** an upvote or a downvote, one per user per review, never on your own review. A review shows its net score (ups minus downs, can be negative), which feeds the default `top` sort.
- **Reports:** a report never hides a review by itself. It goes to a moderation queue, and a moderator hides, dismisses or deletes. Hidden reviews leave the public list and the rating.
- **Developer replies:** one reply per review, from anyone with push access to the GitHub repo (`permissions.push` on `GET /repos/{owner}/{name}` with the replier's token). Any of them can replace or delete it; the author becomes whoever wrote last. Forgejo repos get no replies and no `own_repo` check, since a GitHub identity can't be matched to a forge account.
- **Writes on GitHub repos** resolve the repo with the caller's token, which gives the numeric id and `permissions.push`. A private repo, or one GitHub 404s, is `repo_not_found`.
- **Ratings on cards:** every repo response carries the rating summary, so Home, Explore, Top charts, Search and Library show stars.
- **The backend stays optional:** when it's unreachable, the Reviews section shows a quiet "unavailable" state. The rest of Details is unaffected.

## Identity (backend)

- The token is never logged or stored. On an authed call the backend keys an in-memory cache by `HmacSHA256(pepper, token)`, holding the GitHub user id, login, avatar URL and account creation date for 10 minutes. A miss calls GitHub `GET /user` with the token.
- An upstream 401 returns our **401 `auth_invalid`**: these are the first routes where 401 means "your session with us", which is the convention the backend reserved it for. A token that isn't GitHub-token-shaped (only `[A-Za-z0-9_]`, at most 255 characters) gets the same 401 without a call to GitHub.
- Text, version, platform and app label are trimmed before they're stored; text with a NUL character is `400 invalid_request`.
- Votes and reports need the same 30-day account age as reviews (`account_too_new`). Replies don't check it: push access is the gate there, resolved by the repo's numeric id (`GET /repositories/{id}`), so a renamed repo keeps working; a repo GitHub can't show that token is `403 not_maintainer`.
- In a moderation queue item, `reports` lists every report except dismissed ones, so the hidden queue still shows why a review was hidden.
- Each review stores the author's GitHub id plus a snapshot of their login and avatar, refreshed whenever they write.

## API (all under `/v1`, JSON, additive-only)

Repo routes live under `/reviews/repo/{owner}/{name}`, so an owner named `item` or `moderation` can never collide with the review or moderation routes. They take `host` as a query parameter (default `github.com`; `codeberg.org` or another Forgejo host otherwise). Owner and name are validated like every other repo route.

### Public reads: no token, CDN-cacheable

**`GET /reviews/repo/{owner}/{name}?host=&sort=&rating=&page=&limit=`**
- `sort`: `top` (default), `newest`, `highest` or `lowest`.
- `rating`: a filter from 1 to 5.
- `page`: from 1. `limit`: 1 to 50, default 20. Out-of-range values are clamped (page 1 to 500, limit 1 to 50), as on `/feed`; an unknown `sort` or a `rating` outside 1 to 5 is `400 invalid_request`.
- Cache: `max-age=60, s-maxage=300`.
- The backend never reads `X-GitHub-Token` on this route, so the response is the same for everyone.

```json
{
  "summary": { "average": 4.3, "count": 128, "histogram": { "1": 3, "2": 4, "3": 10, "4": 30, "5": 81 } },
  "reviews": [ Review ],
  "page": 1,
  "hasMore": true
}
```

`Review`:

```json
{
  "id": "rv_8f2c…",
  "rating": 5,
  "text": "Works great on my Pixel.",
  "version": "v1.9.3",
  "platform": "android",
  "appLabel": "Auth",
  "author": { "id": 583231, "login": "octocat", "avatarUrl": "https://…" },
  "createdAt": "2026-10-25T10:00:00Z",
  "updatedAt": "2026-10-25T10:00:00Z",
  "edited": false,
  "score": 9,
  "hidden": false,
  "reply": { "author": { … }, "text": "Thanks!", "createdAt": "…", "updatedAt": "…" }
}
```

- `text`, `version`, `platform`, `appLabel` and `reply` may be null. `platform` is one of `android`, `windows`, `macos` or `linux`.
- `id` is opaque: `rv_` plus 22 base64url characters.
- `hidden` is always false on the public list. It's true in `/mine` when a moderator hid the caller's review, and in the moderation queue. Editing a hidden review keeps it hidden.
- `edited` is true once a PUT has replaced the review.
- `summary.average` is rounded to one decimal, and `histogram` always has all five keys. A repo with no reviews returns `summary.count = 0`, a null `average` and an empty list (a 200, not a 404).
- `score` is upvotes minus downvotes and can be negative; the separate counts aren't published.
- Sorts: `top` is score, then updatedAt, both descending; `newest` is updatedAt descending (an edit bumps it); `highest` and `lowest` sort by rating, then score and updatedAt descending. `page` is capped at 500.

**`GET /reviews/summaries?r={owner}/{name}&r=…&host=`**
- Up to 50 repos (more is `400 invalid_request`). Used for Library cards, whose rows don't come from catalog responses.
- Returns `{ "summaries": { "owner/name": { "average": 4.3, "count": 128 } } }`, keyed by the `r` values exactly as sent. Repos without reviews, and `r` values that fail owner/name validation, are left out. GitHub lookups are case-insensitive.
- Cache: `max-age=300, s-maxage=900`.

**Repo responses:** every `RepoResponse` gains `rating: { "average": 4.3, "count": 128 } | null`. That covers categories, topics, feed, search, `GET /repo` and explore. It's null when the repo has no visible reviews.

### Personal state: token required, `no-store`

**`GET /reviews/repo/{owner}/{name}/mine?host=`**

```json
{
  "review": Review | null,
  "votes": { "rv_…": 1, "rv_…": -1 },
  "canReview": true,
  "cannotReviewReason": null,
  "canReply": false,
  "isModerator": false
}
```

`cannotReviewReason` is one of `banned`, `account_too_new` or `own_repo`, checked in that order (the same order as on `PUT`).

### Writes: token required, `no-store`

| Call | Body | Success |
|---|---|---|
| `PUT /reviews/repo/{owner}/{name}?host=` | `{ rating, text?, version?, platform?, appLabel? }` | `200` with the `Review` (creates or replaces yours) |
| `DELETE /reviews/repo/{owner}/{name}?host=` | none | `204`, also when you have no review |
| `PUT /reviews/item/{reviewId}/vote` | `{ value: 1 \| -1 }` | `204`; sets or replaces your vote, the same value again is a no-op |
| `DELETE /reviews/item/{reviewId}/vote` | none | `204` (idempotent) |
| `POST /reviews/item/{reviewId}/report` | `{ reason: "spam"\|"offensive"\|"off_topic"\|"other", note? }` (note ≤ 500) | `202`; a second report from the same user is a no-op |
| `PUT /reviews/item/{reviewId}/reply` | `{ text }` (≤ 2000, not blank) | `200` with the reply, the same object as `Review.reply` |
| `DELETE /reviews/item/{reviewId}/reply` | none | `204` |

- Deleting your own review works even when you're banned or your account is too new, and doesn't count against the per-user review limit.
- A banned user gets `403 banned` on every other write.
- Votes, reports and replies on a hidden or missing review are `404 review_not_found`. Reporting your own review is `403 own_review`.

### Moderation: moderator token or `X-Admin-Token`, `no-store`

A moderator is a GitHub user whose id is listed in `REVIEW_MODERATOR_IDS` (a comma list), signed in as usual. The operator's `X-Admin-Token` works too.

| Call | Purpose |
|---|---|
| `GET /reviews/moderation/queue?status=reported\|hidden&page=` | 20 per page. `reported`: reviews with open reports, newest report first. `hidden`: hidden reviews, most recently hidden first. |
| `POST /reviews/moderation/item/{reviewId}/hide` `{ reason? }` | Hide it and close its open reports; it leaves the public list and the rating. |
| `POST /reviews/moderation/item/{reviewId}/unhide` | Restore it. |
| `POST /reviews/moderation/item/{reviewId}/dismiss` | Close its open reports and keep it visible. |
| `DELETE /reviews/moderation/item/{reviewId}` | Hard delete. |
| `POST /reviews/moderation/users/{githubId}/ban` `{ reason? }` · `DELETE …/ban` | Stop, or allow again, reviews, replies, votes and reports from that user. Idempotent. |

```json
{
  "items": [
    {
      "review": Review,
      "repo": { "host": "github.com", "owner": "…", "name": "…" },
      "reportCount": 3,
      "reports": [ { "reason": "spam", "note": "…" | null, "createdAt": "…" } ],
      "lastReportedAt": "…" | null,
      "hidden": false,
      "hiddenReason": null
    }
  ],
  "page": 1,
  "hasMore": false
}
```

- Reporter identities are never exposed.
- Every action returns `204`; an unknown review is `404 review_not_found`. A `reason` over 500 code points is `422 note_too_long`.
- No `X-GitHub-Token` and no valid `X-Admin-Token` is `401 auth_required`; a valid token whose id isn't listed is `403 not_moderator`.
- Every moderation action is written to an audit log: the moderator's id, the action, the target and the time.

### Errors

Errors use the backend's existing `ApiError` body, `{ "error": "<code>", "message"? }`.

| Status | Codes |
|---|---|
| 400 | `invalid_owner` / `invalid_name` · `invalid_host` (not github.com or a trusted forge) · `invalid_request` (bad JSON, `version` / `appLabel` over 128, `page` / `limit` / `sort` / `rating` / `status` out of range) |
| 401 | `auth_required` (no token) · `auth_invalid` (GitHub rejected it) |
| 403 | `account_too_new` · `own_repo` · `own_review` (voting on or reporting yourself) · `banned` · `not_maintainer` (reply) · `not_moderator` |
| 404 | `repo_not_found` · `review_not_found` |
| 413 | `payload_too_large` (a body over 8 KB) |
| 422 | `invalid_rating` · `text_too_long` · `note_too_long` · `invalid_platform` · `invalid_reason` |
| 429 | `rate_limited`, with `Retry-After` |
| 502 | `upstream_unavailable` (GitHub unreachable or rate-limited while resolving the user or the repo) |

### Limits

| What | Limit |
|---|---|
| Reviews and replies | 20 per hour per user |
| Votes | 120 per hour per user |
| Reports | 20 per hour per user |
| Reviews, replies and reports together | 300 per hour per IP (votes excluded: a mobile carrier puts thousands of users behind one IP) |
| Public reads | the normal global bucket |

## Client

Details and the cards both need reviews, and features don't depend on each other, so the shared parts live in core:

- `core/domain`: the `model/reviews` models and `ReviewsRepository`. Every failure is a `ReviewException(kind: ReviewError)`.
- `core/data`: `BackendReviewsApi` (on `BackendApiClient.request`) and `ReviewsRepositoryImpl`. `ReviewsRepository.changes` emits a repo after the viewer posts, edits or deletes, so Details refreshes its section.
- `core/presentation/components/reviews`: `ReviewCard`, `RatingSummaryPanel`, `StarRating`, `StarRatingInput` and the `ReviewUi` models.
- `feature/reviews/presentation`: the full screen, `GithubStoreGraph.ReviewsScreen(owner, repo, sourceHost, openComposer, composerRating, version, appLabel)`.

Behaviour:

- **Errors:** error codes map to `ReviewError` and then to string resources. 400 and 413 are invalid input; a plain 404 or 5xx is unavailable.
- **Token:** sent only on `/mine`, the writes and moderation. Never on the public reads, so they stay shared and anonymous.
- **Details:** a Ratings & reviews section with the summary, Rate this app (five stars; a tap opens the screen with the composer at that rating, prefilled with the installed version, else the selected release, and the monorepo app label), the top 3 reviews and See all. It's hidden while the backend can't be reached, and the stars are hidden for the repo's owner.
- **Full screen:** sort and star filter with paging; the viewer's own review is pinned on top (the public list is a CDN copy that can lag a write) and says when a moderator hid it; votes are optimistic (tapping your current vote again takes it back), sent in tap order per review, and a failure falls back to the last vote the server accepted; report and reply sheets.
- **After the viewer's own write:** for five minutes (the list's `s-maxage`) the repo's list reads carry a `fresh` nonce, which the backend ignores, so they skip the CDN's copy and show the write.
- **Signed in** means a stored token (`isUserLoggedIn`), not a fetched profile. Signing in from the Reviews screen returns to it, and a pending composer opens once `/mine` loads.
- **Moderation screen** (`GithubStoreGraph.ReviewModerationScreen`), opened from the Reviews top bar when `isModerator`: the Reported and Hidden queues, with hide, dismiss, unhide, delete and ban. Decisions take reviews out of the server's queue, so the next page is the one holding the first unseen review (`items / 20 + 1`).
- **Stars on cards:** `CompactRating` (stars plus "4.3 (128)") sits under the owner line of `KomiRepoCard`, apart from the stats row, whose star is GitHub stars. Catalog cards read `rating` through `GithubRepoSummary.rating` and the Home cache; Library rows and group headers read one `/reviews/summaries` call from `AppsViewModel`, made again only when the set of repos changes (or, after a failure, on a change five minutes later). The keys go out as written; a host that fails loses only its own rows.

## Rollout

1. **Backend:** schema, identity, the public reads, personal state, writes and moderation, with tests; deployed behind `REVIEWS_ENABLED`. While it's off, the routes return `404 not_found`, which the client treats as unavailable.
2. **Client:** Details section, full screen and write sheet.
3. **Client:** votes, reports, replies and the moderation screen.
4. **Backend:** the `rating` field on repo responses and `/reviews/summaries`. **Client:** ratings on cards.
5. **Backend:** PRIVACY.md and TERMS.md cover public reviews, deletion and moderation before `REVIEWS_ENABLED` goes on in production.
