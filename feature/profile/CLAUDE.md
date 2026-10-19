# Profile Feature

Account hub: signed-in user card (or sign-in prompt), links to Stars / Favourites / Recently viewed, What's new, Announcements, Tweaks, About, and logout. **Settings live in `feature/tweaks/`**; this module is narrow on purpose. Presentation-only; the user comes from core `UserSessionRepository`.

## Structure

```
feature/profile/presentation/
├── ProfileViewModel / State / Action / Event / Root
├── components/      # ProfileSections, LogoutDialog
├── announcements/   # AnnouncementsViewModel + stateless AnnouncementsScreen, mute sheet
└── whatsnew/        # WhatsNewViewModel + stateless WhatsNewHistoryScreen
```

## Navigation

`GithubStoreGraph.ProfileGraph` > `ProfileScreen`, built by `profileGraph()` in `composeApp/.../app/navigation/ProfileGraph.kt`. Also `AnnouncementsScreen` and `WhatsNewHistoryScreen` (wired in `AppNavigation`).

## Notes

- `ProfileState.session` is sealed (`Loading` / `SignedOut` / `SignedIn`) and seeded from `UserSessionRepository.lastKnownSession` (primed by `MainViewModel` at startup), so the screen opens in the last known state instead of signed-out.
- Stars and Logout rows show only when signed in.
- `AnnouncementsViewModel` and `WhatsNewViewModel` are app-wide: created with `koinViewModel()` at the top of `AppNavigation` (and in `Main.kt` for the what's-new sheet), not inside a route. Their screens take state as parameters.
- `AnnouncementsViewModel.unreadCount` drives the unread marker on the Profile row, the bottom nav and the desktop sidebar.
- `WhatsNewViewModel.pendingEntry` feeds `WhatsNewSheet` (composeApp) once per version code; `markSeen()` persists `lastSeenWhatsNewVersionCode` in `TweaksRepository`. Entries come from `WhatsNewLoader` (impl in composeApp) and reload when the app language changes.
- "Tweaks" tap -> `TweaksScreen`, "About" -> `AboutScreen` (both owned by `feature/tweaks`). Don't add settings here.
