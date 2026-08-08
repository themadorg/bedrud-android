# Bedrud Android

Kotlin + Jetpack Compose + Material 3. Single `:app` module. minSdk 28, compileSdk/targetSdk 37, JDK 17.

## Build & Test

```bash
./gradlew assembleDebug          # Debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease        # Release APK (needs keystore.properties)
./gradlew test                   # Unit tests only (src/test/)
```

No instrumented test directory.

This repo also has its own root `Makefile` wrapping the above plus device, CI-parity and
release steps — `make help` lists them. The two that matter most day to day:

```bash
make check                       # lint + testDebugUnitTest — exactly what CI gates a PR on
make doctor                      # why won't it build here: JDK / SDK / adb / gh / signing
```

For the single command to run before every commit, see **Verify command** under
[Working Agreement](#working-agreement) — it folds install into the same invocation so the
on-device pass doesn't pay Gradle's startup cost a second time.

**Test stack:** JUnit 4, MockK, OkHttp MockWebServer, kotlinx-coroutines-test.
**Test util:** `InMemorySharedPreferences` in `testutil/` — inject into any class taking `SharedPreferences` (InstanceStore, AuthManager). Avoid Android framework dependency.

**Versioning:** there is no version number in the repo. `versionCode` comes from the CI run
number and `versionName` from the dispatched tag, both as `-P` flags (see `app/build.gradle.kts`).
The git tag is the source of truth — `make tag-patch`/`tag-minor`/`tag-major` create tags,
they never edit files, and `make release-beta`/`release-stable` only dispatch `release.yml`.
Never add a version literal to a build file or bump one by hand.

## Architecture

```
app/src/main/java/com/bedrud/app/
├── BedrudApplication.kt        Koin init + instance migration
├── MainActivity.kt             NavHost, routes, deep links, PiP
├── core/
│   ├── di/AppModule.kt         Koin module (4 singletons)
│   ├── instance/               Multi-instance: InstanceStore → InstanceManager
│   ├── auth/                   AuthManager (encrypted prefs), PasskeyManager, OAuthLoginHandler
│   ├── api/                    Retrofit interfaces: AuthApi, RoomApi, AdminApi + ApiClientFactory
│   ├── livekit/RoomManager.kt  LiveKit room lifecycle, media toggles, chat
│   ├── pip/PipState.kt         PiP state holder
│   └── call/                   CallService + CallConnectionService (telecom integration)
├── models/                     Data classes (Gson-serialized)
└── ui/
    ├── theme/                  Design tokens: Color, Theme, Type, Shape, Dimens, Elevation, Motion
    ├── components/             BedrudButton (5 variants), BedrudCard, DevOnly/DevHintBadge
    └── screens/                Compose screens per route
```

**Navigation routes** in `Routes` object, `MainActivity.kt`:
`ADD_INSTANCE → LOGIN → {EMAIL_LOGIN, REGISTER} → MAIN (bottom nav) → MEETING/{roomName}`
(LOGIN is the sign-in hub: email/password opens EMAIL_LOGIN, passkey + continue-as-guest happen inline. EMAIL_LOGIN also offers password recovery — "Forgot password?" requests a reset email via `auth/forgot-password`; the reset link itself is completed on the server's web page. REGISTER is the account-creation form, reached from the hub's "No account yet? Sign up" prompt (shown only when the server's `registrationEnabled` is set); it posts to `auth/register` and, on success, immediately signs the new account in.)

No ViewModels. State in `MutableStateFlow` on manager classes (RoomManager, AuthManager, InstanceManager) and screen-level stores (SettingsStore). Collected in composables via `collectAsState()`.

## Multi-Instance Model

App connects to user-chosen Bedrud server instances, not fixed backend.

- `InstanceStore` — persists instance list + active ID in plain `SharedPreferences` ("bedrud_instances")
- `InstanceManager` — central wiring hub. `rebuild()` creates fresh `AuthManager`, `ApiClientFactory`, Retrofit interfaces, `PasskeyManager`, and `RoomManager` for active instance. All exposed as `StateFlow<T?>`.
- `AuthManager` — per-instance `EncryptedSharedPreferences` ("bedrud_secure_$instanceId") storing JWT tokens + user JSON.
- API base URL: `{serverURL}/api` (computed property on `Instance`).
- Health check (`GET /api/health`) runs before adding new instance.
- `AddInstanceScreen`'s custom-server field can also be filled by scanning a QR code instead of typing; the scanned text goes through the same `ServerUrlCanonicalizer` as manual/pasted input. Scanning uses ZXing (`com.journeyapps:zxing-android-embedded`) — a pure on-device decoder with no Play Services dependency, chosen after Google Play Services' own code scanner proved unreliable in practice: its module is fetched over network on first use and failed outright (`MlKitException: Failed to scan code`) in a network-restricted test environment. ZXing needs the CAMERA permission this app already holds for calls; its own capture activity requests it if somehow missing. **Standard follow-up work, not done here:** this only decodes a QR code — nothing in this Android-only repo generates one. For "point your camera at the admin's screen" onboarding to actually work, a self-hosted Bedrud server's admin panel needs its own page that renders a QR code encoding its own address. That's backend/admin-UI work; this repo only has the Android client (backend was stripped out, see git history).

Switching instances: `instanceManager.switchTo(id)` → sets active → rebuilds all clients → UI reacts to StateFlow changes. `InstanceSwitcherSheet` is the shared bottom sheet for this, reachable from the Profile tab's Server section and from tapping the rooms dashboard's header title.

The rooms dashboard (`DashboardContent`) lists the **active** server's rooms from the API and weaves in **recent** rooms from every server (`RecentRoomsStore`, which stores each recent's server id, name, and accent color). Its **All** tab merges both (recency/live first); **My Rooms** is the subset the user created. Each card is tinted with its server's color, and tapping a recent that lives on another server prompts a confirm-and-switch (`switchTo` + join) rather than switching silently.

## Networking

Retrofit + OkHttp + Gson (not kotlinx-serialization for HTTP). `kotlin-serialization` plugin enabled but used elsewhere.

- `AuthInterceptor` — attaches `Authorization: Bearer <token>` to every request
- `TokenAuthenticator` — handles 401 by refreshing token synchronously (creates separate Retrofit to avoid recursion), retries once, forces logout on failure
- Base URL format: `https://host/api/` (trailing slash appended by `ApiClientFactory`)

## Key Conventions

- **Design tokens:** All sizes/spacing/curves/colors/motion come from `ui/theme/` (`Dimens`, `BedrudShapeTokens`, `Elevation`, `Motion`, `MaterialTheme.colorScheme/typography/shapes`). No raw `n.dp` or hex literals in `ui/screens/**` or `ui/components/**`. See [DESIGN.md](DESIGN.md).
- **Buttons:** Use `BedrudButton` with `BedrudButtonVariant` enum (PRIMARY, SECONDARY, OUTLINE, GHOST, DESTRUCTIVE). Height/shape/padding are token-driven (`Dimens.buttonHeight`, `BedrudShapeTokens.button`); grow via `Modifier.height(Dimens.buttonHeightLarge)` for a full CTA.
- **Cards:** Use `BedrudCard` / `BedrudOutlinedCard` — outline-first, tonal surface, minimal elevation.
- **Colors:** Always `MaterialTheme.colorScheme.*`. Rose (`#E11D48`) primary + teal (`#14B8A6`) tertiary on warm neutrals; the full M3 role set (light+dark) is mapped in `ui/theme/Theme.kt` from the ramps in `Color.kt`. `dynamicColor` is off by default.
- **Serialization:** `@SerializedName` annotations on model fields (Gson). Snake_case from server ↔ camelCase in Kotlin.
- **DI:** Koin. Single module (`appModule`). Inject with `by inject()` in Activities, `by koinViewModel()` or `koinInject()` in composables.
- **Strings:** User-facing strings go in `res/values/strings.xml` **and must be translated in every locale** (ar, de, es, fa, fr, ja, ru, tr, zh) — CI lint fails on `MissingTranslation`, so English-only is not enough. RTL supported (Vazirmatn/Shabnam via `LocaleHelper`).
- **Input:** Validate/format user input per its type (trim/strip whitespace, validate URL/email shape); never treat malformed input as valid.
- **Keyboard:** The IME may cover the primary button, but the focused input must stay visible — make content scroll into view (ime-aware: `WindowInsets.ime` / `imePadding`), wherever reasonable, so the user sees what they type. The action key should dismiss the keyboard + run the primary action. App is edge-to-edge → react to ime insets, not window resizing.
- **Dev-only UI:** Gate not-yet-wired UI or QA aids with `DevOnly { … }` / `DevHintBadge("…")` (visible on debug/`dev`, hidden on release) — backed by `BuildConfig.DEV_HINTS` via `core/DevFlags.kt`.

## Working Agreement

<!-- working-agreement: confirmed 2026-08-08 -->

The maintainer's standing rules were reviewed rule by rule and **confirmed for this repo on
2026-08-08 with no overrides** — every one applies as written. This section records only what is
specific to bedrud, and corrects two rules this file previously stated out of date.

### Project slots

| Slot | Value |
|---|---|
| **Verify command** | `./gradlew lint testDebugUnitTest installDev -q` — one invocation, run in full before every commit. During rapid iteration run `./gradlew installDev -q` alone: it compiles and fails just as loudly, and lint/test aren't install dependencies so they wouldn't run anyway. `lint` + `testDebugUnitTest` are exactly what `pr-build.yml` gates a PR on. |
| **Default branch** | `master`. The repo **squash-merges**, so `git branch --merged` never detects a merged feature branch — its tip SHA differs from what landed. Detect via `[origin/<name>: gone]` in `git branch -vv`, or a merged PR whose `headRefName` matches. |
| **Design system** | Newest Material 3 incl. Expressive. Tokens in `ui/theme/`; rose `#E11D48` primary + teal `#14B8A6` tertiary on warm neutrals, full light+dark role set mapped in `Theme.kt`, `dynamicColor` off. |
| **Locales** | 9 — ar, de, es, fa, fr, ja, ru, tr, zh. Lint treats `MissingTranslation` as an error and fails CI, so English-only is never enough. |
| **Run target** | `dev` channel on the wired device (`applicationIdSuffix = ".dev"`, so it coexists with a stable install). Drive via adb: `screencap`, `input tap`, `dumpsys`, `logcat`. |
| **Issues** | `[TASK]:` / `[BUG]:` titles, `- [ ]` checklist bodies, labels `roadmap` and `tech-debt` (both already exist — reuse, don't recreate). Every PR carries `Closes #N` so its issue auto-closes on merge. |

### Overrides

None. Every rule was confirmed unchanged. One was **strengthened**: always `git fetch` and resolve
against `origin/master` before reading history or inspecting the tree — a stale local checkout
otherwise reports files as absent that are already on master.

### Corrections to what this section previously said

Two rules changed after this section was first written, and it had not caught up:

- **Never commit, push, or open/update a PR without explicit approval** (2026-08-02, reaffirmed
  2026-08-06). This supersedes the earlier "commit + push the branch as you go to back it up".
  Implement → verify → **stop** → report → wait for the explicit word. Applies mid-iteration and to
  draft PRs; a specific change request is not itself approval to ship.
- **Verify on-device yourself — don't hand it off** (2026-07-29). This supersedes the earlier
  "the maintainer runs it themselves … don't self-run/screenshot the device". Build, `installDev`,
  then drive via adb and report what you actually observed. The maintainer still signs off.

### Doing UI/UX work

The app's UI/UX is being reworked screen by screen. When doing this work:

- **Sketches are intent, not spec.** Implement to the **newest official Material 3** guidelines
  (including M3 Expressive — the Compose BOM is current), not a literal trace of the sketch.
- **Recommend, then implement — every change.** Lead with your recommendation/opinion on any UI/UX
  change (the initial sketch AND any review tweak) and let the maintainer decide before you code it.
  Don't jump straight to implementing a change request.
- **Design system first.** Reuse and extend the token layer (`ui/theme/`) and shared components. No
  magic numbers or hex in screens — see the Design tokens convention above and [DESIGN.md](DESIGN.md).
- **Keep the brand coherent.** Rose + teal on warm neutrals, rounded, M3-native. Change the palette only
  in `Color.kt`/`Theme.kt`, never per-screen.
- **Unbuilt features get a dev-only hint.** If UI has no backing functionality yet, build it and mark it
  with `DevOnly`/`DevHintBadge` so it never misleads release users.
- **Keep the repo in sync.** A user-facing capability updates the README **Feature list**; internal or
  refactor work updates this file and DESIGN.md — not the README. Never document a feature before it
  ships. `strings.xml` and all 9 locales land in the same change.
- **One page = one unit.** Its own branch cut fresh from `master`, in its own worktree, never stacked on
  another page's branch. Open the PR only when the page is complete — after every review iteration and
  on-device approval — so the PR and its description cover all the work from the start.

## Release Signing

Requires `keystore.properties` at project root (gitignored):
```properties
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, release builds fail. Debug builds work without it.

## ProGuard

Release builds use minification + resource shrinking. Rules in `app/proguard-rules.pro` keep LiveKit, Retrofit interfaces, `models.**`, and Credential Manager classes.

## Deep Links

- `https://bedrud.com/m/{roomName}` → join room
- `https://bedrud.com/c/{roomName}` → join room
- `bedrud://oauth` → OAuth callback (expects `?token=...`)

Parsed in `BedrudURLParser`, handled in `MainActivity.handleDeepLink()`.

## Skills Reference

| Skill                         | When to Use                                                             | Example Scenarios for Bedrud                                                                          |
|-------------------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| **android-accessibility**     | Auditing or fixing accessibility issues in Compose UI                   | Adding contentDescription to buttons, ensuring screen reader compatibility for meeting controls       |
| **android-architecture**      | Setting up project structure, modules, or dependency injection          | Adding new feature modules, restructuring to support new authentication flow, optimizing Koin modules |
| **android-gradle-logic**      | Setting up scalable Gradle build configuration                          | Adding Convention Plugins, managing Version Catalogs, optimizing build times                          |
| **android-jetpack-compose**   | Building new UI screens or managing Compose state                       | Creating new meeting screens, implementing room control UI, handling remember/mutableStateOf          |
| **android-kotlin**            | General Android Kotlin development, coroutines, testing                 | Writing coroutines for network calls, using MockK for unit tests, Hilt injection                      |
| **compose-performance-audit** | Diagnosing slow rendering, janky scrolling, or excessive recompositions | Optimizing meeting room list scrolling, reducing RoomManager state collection overhead                |
| **compose-ui**                | Writing or refactoring Composables with best practices                  | Implementing state hoisting in MeetingScreen, optimizing component recomposition, applying theming    |
| **kotlin-concurrency-expert** | Reviewing or fixing coroutine/thread-safety issues                      | Resolving race conditions in RoomManager, fixing lifecycle scope issues                               |
| **gradle-build-performance**  | Debugging slow builds or CI/CD performance                              | Analyzing build scans, identifying compilation bottlenecks in multi-instance setup                    |
| **xml-to-compose-migration**  | Converting legacy XML layouts to Compose                                | Migrating any old View-based layouts (if any remain) to Compose components                            |
| **Kotlin Error Debugging**    | Debugging complex Kotlin errors or coroutine stack traces               | Debugging StateFlow emission issues, platform type warnings, or crashes                               |
