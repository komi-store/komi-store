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
  - optional text of up to 2000 characters;
  - the version reviewed (a release tag), the platform, and an optional app label.
- **Helpful votes:** one per user per review, never on your own review. They feed the default sort.
- **Reports:** a report never hides a review by itself. It goes to a moderation queue, and a moderator hides, dismisses or deletes. Hidden reviews leave the public list and the rating.
- **Developer replies:** one reply per review, from anyone with push access to the GitHub repo (`permissions.push` on `GET /repos/{owner}/{name}` with the replier's token). Forgejo repos get no replies, since a GitHub identity can't be matched to a forge account.
- **Ratings on cards:** every repo response carries the rating summary, so Home, Explore, Top charts, Search and Library show stars.
- **The backend stays optional:** when it's unreachable, the Reviews section shows a quiet "unavailable" state. The rest of Details is unaffected.

## Identity (backend)

- The token is never logged or stored. On an authed call the backend keys an in-memory cache by `HmacSHA256(pepper, token)`, holding the GitHub user id, login, avatar URL and account creation date for 10 minutes. A miss calls GitHub `GET /user` with the token.
- An upstream 401 returns our **401 `auth_invalid`**: these are the first routes where 401 means "your session with us", which is the convention the backend reserved it for.
- Each review stores the author's GitHub id plus a snapshot of their login and avatar, refreshed whenever they write.

## API (all under `/v1`, JSON, additive-only)

Repo routes live under `/reviews/repo/{owner}/{name}`, so an owner named `item` or `moderation` can never collide with the review or moderation routes. They take `host` as a query parameter (default `github.com`; `codeberg.org` or another Forgejo host otherwise). Owner and name are validated like every other repo route.

### Public reads: no token, CDN-cacheable

**`GET /reviews/repo/{owner}/{name}?host=&sort=&rating=&page=&limit=`**
- `sort`: `helpful` (default), `newest`, `highest` or `lowest`.
- `rating`: a filter from 1 to 5.
- `page`: from 1. `limit`: 1 to 50, default 20.
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
  "helpfulCount": 12,
  "reply": { "author": { … }, "text": "Thanks!", "createdAt": "…", "updatedAt": "…" }
}
```

`text`, `version`, `platform`, `appLabel` and `reply` may be null. `platform` is one of `android`, `windows`, `macos` or `linux`. A repo with no reviews returns `summary.count = 0`, a null `average` and an empty list (a 200, not a 404).

**`GET /reviews/summaries?r={owner}/{name}&r=…&host=`**
- Up to 50 repos. Used for Library cards, whose rows don't come from catalog responses.
- Returns `{ "summaries": { "owner/name": { "average": 4.3, "count": 128 } } }`. Repos without reviews are left out.
- Cache: `max-age=300, s-maxage=900`.

**Repo responses:** every `RepoResponse` gains `rating: { "average": 4.3, "count": 128 } | null`. That covers categories, topics, feed, search, `GET /repo` and explore. It's null when the repo has no visible reviews.

### Personal state: token required, `no-store`

**`GET /reviews/repo/{owner}/{name}/mine?host=`**

```json
{
  "review": Review | null,
  "votedReviewIds": ["rv_…"],
  "canReview": true,
  "cannotReviewReason": null,
  "canReply": false,
  "isModerator": false
}
```

`cannotReviewReason` is one of `account_too_new`, `own_repo` or `banned`.

### Writes: token required, `no-store`

| Call | Body | Success |
|---|---|---|
| `PUT /reviews/repo/{owner}/{name}?host=` | `{ rating, text?, version?, platform?, appLabel? }` | `200` with the `Review` (creates or replaces yours) |
| `DELETE /reviews/repo/{owner}/{name}?host=` | none | `204` |
| `PUT /reviews/item/{reviewId}/helpful` | none | `204` (idempotent) |
| `DELETE /reviews/item/{reviewId}/helpful` | none | `204` |
| `POST /reviews/item/{reviewId}/report` | `{ reason: "spam"\|"offensive"\|"off_topic"\|"other", note? }` (note ≤ 500) | `202` (idempotent per user) |
| `PUT /reviews/item/{reviewId}/reply` | `{ text }` (≤ 2000) | `200` with the reply |
| `DELETE /reviews/item/{reviewId}/reply` | none | `204` |

### Moderation: moderator token or `X-Admin-Token`, `no-store`

A moderator is a GitHub user whose id is listed in `REVIEW_MODERATOR_IDS` (a comma list), signed in as usual. The operator's `X-Admin-Token` works too.

| Call | Purpose |
|---|---|
| `GET /reviews/moderation/queue?status=reported\|hidden&page=` | Reviews with open reports, newest report first. Each entry has the review, the repo, the report count, its reasons and notes, and whether it's hidden. |
| `POST /reviews/moderation/item/{reviewId}/hide` `{ reason }` | Hide it; it leaves the public list and the rating. |
| `POST /reviews/moderation/item/{reviewId}/unhide` | Restore it. |
| `POST /reviews/moderation/item/{reviewId}/dismiss` | Close its open reports and keep it visible. |
| `DELETE /reviews/moderation/item/{reviewId}` | Hard delete. |
| `POST /reviews/moderation/users/{githubId}/ban` `{ reason }` · `DELETE …/ban` | Stop, or allow again, reviews, replies, votes and reports from that user. |

Every moderation action is written to an audit log: the moderator's id, the action, the target and the time.

### Errors

Errors use the backend's existing `ApiError` body, `{ "error": "<code>", "message"? }`.

| Status | Codes |
|---|---|
| 400 | `invalid_owner` / `invalid_name` |
| 401 | `auth_required` (no token) · `auth_invalid` (GitHub rejected it) |
| 403 | `account_too_new` · `own_repo` · `own_review` (voting on yourself) · `banned` · `not_maintainer` (reply) · `not_moderator` |
| 404 | `repo_not_found` · `review_not_found` |
| 422 | `invalid_rating` · `text_too_long` · `note_too_long` · `invalid_platform` · `invalid_reason` |
| 429 | `rate_limited`, with `Retry-After` |
| 502 | `upstream_unavailable` (GitHub unreachable while resolving the user) |

### Limits

| What | Limit |
|---|---|
| Reviews and replies | 20 per hour per user |
| Votes | 120 per hour per user |
| Reports | 20 per hour per user |
| All writes together | 60 per hour per IP |
| Public reads | the normal global bucket |

## Client

The `feature/reviews` module has `domain`, `data` and `presentation` layers. Backend calls live in `BackendApiClient`, like every other backend call.

- **Errors:** typed `ReviewError` values, mapped from the error codes above to string resources.
- **Token:** sent only on `/mine`, the writes and moderation. Never on the public reads, so they stay shared and anonymous.
- **Details:** a Reviews section with the average, the histogram, the top 3 reviews and a Write / Edit button.
  - The button sits behind sign-in, and `cannotReviewReason` is shown instead when it's set.
  - The write sheet prefills the version from the installed app, or else the selected release.
- **Full Reviews screen** (`GithubStoreGraph.ReviewsScreen(owner, repo, sourceHost)`):
  - sort and star filter, with paging;
  - helpful votes, report and reply sheets;
  - a just-written review is merged in locally until the CDN copy catches up.
- **Moderation screen** for moderators (`isModerator`): the queue, plus hide, unhide, dismiss, delete and ban.
- **Cards:** catalog cards show the average and count from `rating`; Library rows use `/reviews/summaries`.
- **Translate:** review text gets the same Translate action as release notes.

## Rollout

1. **Backend:** schema, identity, the public reads, personal state, writes and moderation, with tests; deployed behind `REVIEWS_ENABLED`. While it's off, the routes return `404 not_found`, which the client treats as unavailable.
2. **Client:** Details section, full screen and write sheet.
3. **Client:** votes, reports, replies and the moderation screen.
4. **Backend:** the `rating` field on repo responses and `/reviews/summaries`. **Client:** ratings on cards.
5. **Backend:** PRIVACY.md and TERMS.md cover public reviews, deletion and moderation before `REVIEWS_ENABLED` goes on in production.
