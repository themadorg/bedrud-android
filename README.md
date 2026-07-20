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

## Features

- **Video & audio meetings** — WebRTC rooms powered by the [LiveKit Android SDK](https://github.com/livekit/client-sdk-android)
- **Connect to multiple servers** — add several Bedrud instances and switch between them; each keeps its own login
- **Meetings as real calls** — joining starts a self-managed telecom call, so meetings get proper audio routing, a call notification with mute/hangup, and survive backgrounding
- **Picture-in-Picture** — keep the meeting visible while using other apps
- **Screen sharing** and an adaptive video grid
- **In-meeting chat** with image sharing, over the LiveKit data channel
- **Shared stage** — screenshare and coordinated peer-to-peer
- **Admin moderation** — kick, ban, mute, and stage controls for room hosts
- **Flexible sign-in** — email/password, guest access, OAuth, and FIDO2 passkeys
- **Localized** — 10 languages with right-to-left (Arabic, Persian) support

## Requirements

- Android 9 (API 28) or newer
- One or more reachable Bedrud server instances

## Getting started (as a user)

1. Install the app (build it yourself — see below — or grab a release APK).
2. On first launch, **add a server instance** by entering your Bedrud server URL.
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

| Concern       | Choice                                                                    |
|---------------|---------------------------------------------------------------------------|
| UI            | Jetpack Compose + Material 3                                               |
| DI            | Koin                                                                       |
| Networking    | Retrofit + OkHttp (Gson)                                                   |
| Realtime media| LiveKit Android SDK                                                        |
| Calls         | Self-managed telecom `ConnectionService` (foreground service)             |
| Auth storage  | `EncryptedSharedPreferences`, per instance                                |
| Passkeys      | AndroidX Credential Manager + Play Services FIDO                          |
| Images        | Coil                                                                       |

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
  meeting/    meeting logic + stage/ peer-to-peer stage protocol
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
