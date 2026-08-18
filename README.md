# Bedrud for Android

The official Android client for **Bedrud** — a self-hostable, LiveKit-powered video meeting platform.

This app is a pure client. It doesn't run a server; you point it at one or more Bedrud
server instances (`https://your-server/api`) and it handles auth, rooms, and live meetings.
For the server, web app, and other platforms, see the [main Bedrud project](https://github.com/themadorg/bedrud).

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-API%2028+-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

---

## Feature

- **Video & audio meetings** — WebRTC rooms powered by the [LiveKit Android SDK](https://github.com/livekit/client-sdk-android)
- **Connect to multiple servers** — add several Bedrud instances and switch between them; each keeps its own login
- **Add a server by QR** — scan a Bedrud server's QR code to fill in its URL instead of typing it
- **Meetings as real calls** — joining starts a self-managed telecom call, so meetings get proper audio routing, a call notification with mute/hangup, and survive backgrounding
- **Picture-in-Picture** — keep the meeting visible while using other apps
- **Screen sharing** — several people can present at once, and watching a stream is opt-in per viewer
- **In-meeting chat** over the LiveKit data channel — messages grouped by sender, selectable text,
  and image sharing with any received picture savable to the gallery
- **Push to talk & voice sensitivity** — hold-to-talk input mode, a manual voice gate for voice activity, per-person volume, and device noise suppression
- **Admin moderation** — kick and ban controls for room hosts
- **Flexible sign-in** — email/password, guest access, OAuth, and FIDO2 passkeys
- **Localized** — 10 languages with right-to-left (Arabic, Persian) support

## Requirements

- Android 9 (API 28) or newer
- One or more reachable Bedrud server instances

## Getting started (as a user)

1. Install the app (build it yourself — see below — or grab a release APK).
2. On first launch, **choose a server** — continue with the default public server, or enter your own
   Bedrud server URL. You can add more servers and switch between them later.
3. Sign in (or join as a guest), then create or join a room.

## Building from source

You'll need the **Android SDK** and **JDK 17**. Android Studio (latest stable) is the
easiest path — open the project and let it sync.

From the command line:

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires a keystore, see below)
./gradlew assembleRelease

# Run unit tests
./gradlew test
```

Debug APKs land in `app/build/outputs/apk/`. Builds are split per ABI
(`arm64-v8a`, `armeabi-v7a`, `x86_64`) with a universal APK also produced.

### Make targets

A root `Makefile` wraps the commands above, plus the device, CI-parity, and release
steps. It only launches `gradlew`, `adb`, `git` and `gh` — nothing is reimplemented, so
the Gradle commands stay valid if you prefer them.

```bash
make help      # every target, grouped
make doctor    # check JDK / SDK / adb / gh, and whether signing is configured

make build           # debug APKs
make build-dev       # dev APKs — install side by side with the real app
make build-release   # release APKs

make install         # build + install debug   (also: install-dev, uninstall, run)
make logcat          # tail logcat for this app only

make check           # lint + unit tests — exactly what CI gates a PR on
make clean
```

Builds accept `SERVER=<host>` as a shorthand for `-PdefaultServerHost` (see below), e.g.
`make build-dev SERVER=staging.example.com`.

Releases are cut from git tags — there is no version number stored in the repo (see
[Versioning](#versioning)):

```bash
make version                     # current tag, next patch/minor/major, release state
make tag-patch                   # create the next tag locally (also: tag-minor, tag-major)
make tag-push                    # push it
make release-beta   TAG=1.3.1    # dispatch the signed release workflow for that tag
make release-stable TAG=1.3.1    # …or promote it to stable
make release-status              # recent release runs
```

On Windows, run these from Git Bash or WSL. `make` isn't bundled with Git for Windows —
install it with `winget install ezwinports.make` or `scoop install make`.

### Versioning

`versionName` and `versionCode` are not stored in the repo. `versionCode` comes from the
CI run number and `versionName` from the tag the release workflow was dispatched against,
both passed to Gradle as `-P` flags (see `app/build.gradle.kts`). So the git tag is the
single source of truth, and nothing needs a manual bump — `make tag-patch` and friends
create tags, they don't edit files.

Building and signing a release happens only in `.github/workflows/release.yml`, which is
dispatched manually against a tag, gated on lint and unit tests passing for that exact
commit, and on approval from the `beta-signing` / `production-signing` environments. The
`make release-*` targets start that run; they never sign anything locally.

### Default server host

The Add Instance screen pre-fills `bedrud.xyz` as the server host. To ship a build that
defaults to a different instance (for example a staging or self-hosted deployment), pass
`-PdefaultServerHost` at build time — no code changes needed:

```bash
./gradlew assembleRelease -PdefaultServerHost=meet.example.com
```

The value is baked into `BuildConfig.DEFAULT_SERVER_HOST`; when the flag is omitted, builds
fall back to `bedrud.xyz`. Users can still change the host on the Add Instance screen either way.

### Release signing (optional)

For a signed release build, create a `keystore.properties` file in the project root:

```properties
storeFile=/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

If the file is absent, release builds are simply left unsigned.

## Architecture

Single-module Kotlin app (`com.bedrud.app`) built with Jetpack Compose and Material 3.
There are no ViewModels — screen state lives in `MutableStateFlow` on manager classes and
is collected with `collectAsState()`.

| Concern        | Choice                                                        |
|----------------|---------------------------------------------------------------|
| UI             | Jetpack Compose + Material 3                                  |
| DI             | Koin                                                          |
| Networking     | Retrofit + OkHttp (Gson)                                      |
| Realtime media | LiveKit Android SDK                                           |
| Calls          | Self-managed telecom `ConnectionService` (foreground service) |
| Auth storage   | `EncryptedSharedPreferences`, per instance                    |
| Passkeys       | AndroidX Credential Manager + Play Services FIDO              |
| Images         | Coil                                                          |

**Multi-instance is the spine of the app.** `InstanceManager` rebuilds the auth manager,
Retrofit APIs, and the LiveKit `RoomManager` for whichever server is active, and the UI
reacts to the swap. This is why every login, room list, and meeting is scoped to a server.

Source layout (`app/src/main/java/com/bedrud/app/`):

```
core/
  api/        Retrofit services + DTOs
  auth/       login, tokens, passkeys
  call/       telecom ConnectionService (meetings-as-calls)
  chat/       in-meeting chat
  deeplink/   /m/ and /c/ link handling
  di/         Koin modules
  instance/   multi-server management
  livekit/    RoomManager — LiveKit lifecycle
  meeting/    meeting logic (chat wire, video aspect)
  pip/        picture-in-picture state
  recent/     recent rooms
ui/
  components/ shared Compose widgets
  screens/    auth, dashboard, instance, main, meeting, admin, profile, settings
  theme/      colors, typography
```

## Project docs

- [AGENTS.md](AGENTS.md) — developer guide and conventions
- [DESIGN.md](DESIGN.md) — design system notes
- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute
- [README-OLD.md](README-OLD.md) — the original monorepo README (full-stack Bedrud), kept for reference

## License

[Apache-2.0](LICENSE). See [NOTICE](NOTICE) for attributions.
