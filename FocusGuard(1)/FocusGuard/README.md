# Focus Guard / فوکوس گارد

Android app: time management + app locking + per-app usage reports + To-Do + planner.
Kotlin, Jetpack Compose (Material 3), Room, WorkManager. minSdk 26, target 34.

## Open and run
1. Android Studio (Koala or newer) -> Open -> select this folder.
2. If Studio asks about Gradle, let it create the wrapper / use its bundled Gradle (8.7+), then Sync.
3. Run on a real phone (Accessibility and Usage Access cannot be tested properly on some emulators).

## First run on the phone
Home -> "Let's finish setup" lists what to allow:
- Accessibility (recommended, instant blocking, event driven)
- Usage access (reports + backup blocking method)
- Display over other apps (lock screen on top of a blocked app)
- Notifications, Exact alarms, Battery optimization (optional but recommended)

Blocking works if EITHER Accessibility is on, OR Usage access + Overlay are both on.

## Architecture
```
UI (Compose)  ->  MainVm  ->  Room (profiles, sessions, attempts, daily_usage, tasks, plan_blocks, limits)
                      |
FocusService (foreground) -- owns session, Pomodoro phases, exact alarms, notification
      |                        \
BlockEngine (state) <---- Blocker.onForeground(pkg) ----> BlockActivity (lock screen)
      ^                        ^
AppBlockerService          UsageStats watcher (fallback, only when Accessibility is not connected)
(Accessibility, events)
```

## Battery design
- Method 1 (Accessibility) is event driven: no polling at all.
- Method 2 (UsageStats) polls every 1.5 s ONLY during an active session, ONLY while the screen is on,
  and ONLY if the Accessibility service is not connected. When it is connected the watcher sleeps (30 s heartbeat).
- The countdown in the notification is drawn by the system (chronometer), not by the app.
- The in-app ticking clock stops when the app is not visible.
- Usage history is synced every 6 hours with WorkManager, not continuously.

## Strict mode
No early exit, and while the session runs the Settings app and package-installer screens are blocked
(so the service cannot be switched off or the app uninstalled from the phone UI).

## Known limits / notes
- Daily per-app limits need the Accessibility service.
- Google Play requires a declaration form for Accessibility apps; Cafe Bazaar / Myket / direct APK are simpler.
- Fonts: system fonts are used (Persian renders with the system Persian font). To use Vazirmatn, add it to res/font and set it in Theme.kt.
- This project was written without an Android SDK available, so it has NOT been compiled here. Expect to fix small compile warnings/errors on first sync.
