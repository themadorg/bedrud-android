# Contributing

Contributions to the Bedrud Android client are welcome. This guide covers the process for
submitting changes to **this** repository — the Android app. For the server, web app, or
other platforms, see the [main Bedrud project](https://github.com/themadorg/bedrud).

## Getting started

1. Fork the repository
2. Clone your fork
3. Create a feature branch from `master`
4. Make your changes
5. Submit a pull request

## Prerequisites

- **JDK 17** — the project pins `jvmToolchain(17)`
- **Android SDK** (compileSdk 37)
- **Git**

[Android Studio](https://developer.android.com/studio) (latest stable) is the easiest
path: it brings its own JDK and SDK manager, and writes the `local.properties` the build
needs. Everything below also works from the command line.

## Development setup

```bash
# After forking on GitHub, clone your fork
git clone https://github.com/<your-username>/bedrud-android.git
cd bedrud-android

./gradlew assembleDebug
```

Debug APKs land in `app/build/outputs/apk/debug/`, split per ABI plus a universal APK.

### Default server host

The add-server screen offers `bedrud.xyz` as its default server. To ship a build that
defaults to a different one, a staging or self-hosted deployment for example, pass
`-PdefaultServerHost` at build time; no code changes are needed:

```bash
./gradlew assembleRelease -PdefaultServerHost=meet.example.com
```

The value is baked into `BuildConfig.DEFAULT_SERVER_HOST`; without the flag, builds use
`bedrud.xyz`. Users can still enter any other server either way.

### Release signing

A release build is signed with the key a `keystore.properties` file in the project root
names:

```properties
storeFile=/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file, `assembleRelease` stops at `validateSigningRelease`. Debug and dev
builds need no key.

### Make targets

A root `Makefile` wraps the Gradle commands, plus the device, CI-parity and release steps.
It only launches `gradlew`, `adb`, `git` and `gh`; nothing is reimplemented, so the Gradle
commands stay valid if you prefer them.

```bash
make help            # every target, grouped
make doctor          # check JDK / SDK / adb / gh, and whether signing is configured

make build           # debug APKs
make build-dev       # dev APKs, which install side by side with the real app
make build-release   # release APKs

make install         # build + install debug   (also: install-dev, uninstall, run)
make logcat          # tail logcat for this app only

make check           # lint + unit tests, exactly what CI gates a PR on
make coverage        # unit tests, then a report of the lines they reached
make device-test     # instrumented tests on an API 36 emulator Gradle runs itself, as CI does
make clean
```

Builds accept `SERVER=<host>` as a shorthand for `-PdefaultServerHost`, e.g.
`make build-dev SERVER=staging.example.com`.

## Project structure

Single `:app` module, with this source layout under `app/src/main/java/com/bedrud/app/`:

```
core/
  api/        Retrofit services + DTOs
  audio/      voice gate, speech detection, meeting sounds
  auth/       login, tokens, passkeys
  call/       telecom ConnectionService (meetings-as-calls)
  chat/       chat images: upload, decoding, saving
  deeplink/   /m/ and /c/ link handling
  di/         Koin modules
  instance/   multi-server management
  livekit/    RoomManager — LiveKit lifecycle
  meeting/    chat and presence wire formats, video aspect
  pip/        picture-in-picture state
  prefs/      SharedPreferences helpers
  recent/     recent rooms
  rooms/      room activity, rooms waiting to open, join failures
ui/
  components/ shared Compose widgets
  screens/    auth, dashboard, instance, main, meeting, admin, profile, settings
  theme/      design tokens: colors, type, shapes, sizes, elevation, motion
  util/       opening chat links, the clipboard, sharing
```

[ARCHITECTURE.md](ARCHITECTURE.md) maps how those parts connect, and the conventions that
matter when changing them — design tokens, the multi-instance model, navigation,
networking — are in [AGENTS.md](AGENTS.md). The design system itself is documented in
[DESIGN.md](DESIGN.md).

## Code style

Kotlin, Android Studio defaults. Three project-specific rules are worth calling out
because CI or review will catch them:

- **Design tokens.** Sizes, spacing, shapes, colors, and motion come from `ui/theme/`.
  No raw `n.dp` or hex literals under `ui/screens/**` or `ui/components/**`.
- **Strings must be translated.** User-facing strings go in `res/values/strings.xml`
  *and* in every locale (ar, de, es, fa, fr, ja, ru, tr, zh). Lint fails on
  `MissingTranslation`, so an English-only string breaks the build. The app supports RTL.
- **No version literals in build files.** `versionName` and `versionCode` are supplied by
  CI; the git tag is the source of truth. See [Releases](#releases).

## Pull request process

1. **Branch naming:** `feat/description`, `fix/description`, `chore/description`, or
   `docs/description`
2. **Commit messages:** conventional-commit style — `feat(auth): …`, `fix(meeting): …`,
   `build: …` — with a body explaining *why*, not just what
3. **CI checks:** all GitHub Actions checks must pass
4. **Description:** what changed and why; the PR template covers the rest

### CI checks

Every PR runs [`pr-build.yml`](.github/workflows/pr-build.yml):

| Job         | What it does                                                              |
|-------------|---------------------------------------------------------------------------|
| Lint & Test | `./gradlew lint` and `./gradlew testDebugUnitTest`                         |
| Instrumented tests | The instrumented tests on an API 36 emulator, split across three runners; not a required check yet |
| Dev build   | Builds a signed **dev** APK and comments install links on the PR           |

The dev APK has its own application ID (`com.bedrud.app.dev`) and app name, so reviewers
can install it next to a real Bedrud build without the two colliding. The build job is
skipped for Dependabot PRs, which by design cannot reach repository secrets.

### Before submitting

Run the same two checks CI gates on:

```bash
./gradlew lint
./gradlew testDebugUnitTest

# or, the same pair in one command
make check
```

`make help` lists the rest of the shortcuts, and `make doctor` diagnoses a machine that
won't build. On Windows run them from Git Bash or WSL — `make` isn't bundled with Git for
Windows (`winget install ezwinports.make` or `scoop install make`).

### Driving the app on a device

A green build is not evidence the change works, so a change gets run. `make drive`
installs the dev build, launches it and prints every label on screen; from there
[`tools/emu`](tools/emu) walks the app by naming those labels rather than tapping
coordinates, waiting for each one to appear:

```bash
tools/emu tap  "my-room"     30   # seconds to wait before giving up
tools/emu tap  "Toggle Chat" 45   # long enough to cover the LiveKit connect
tools/emu wait "Type a message"
tools/emu shot chat-sheet         # lands in shots/, which is git-ignored
```

Chaining the steps in one shell invocation is the point: a tap costs milliseconds, while
stopping to look at a screenshot between every step costs the rest of the afternoon. Look
once, at the end. `tools/emu log` tails just this app's logcat when a step doesn't land,
and `make screen` re-lists the labels when you need to find the next thing to tap.

One limit worth knowing: `adb`'s `input text` is ASCII-only, and no shell-reachable
substitute exists on current images. Persian, Arabic and emoji have to enter the app some
other way — a second participant sending them, or a fixture in a test.

Tests are JUnit 4 with MockK, OkHttp MockWebServer, and kotlinx-coroutines-test.
`InMemorySharedPreferences` in `testutil/` lets you inject into anything taking
`SharedPreferences` without pulling in the Android framework. The instrumented Compose
tests live in `app/src/androidTest/`; `make device-test` runs them all on an emulator
Gradle starts itself, and [AGENTS.md](AGENTS.md#build--test) shows how to run one class
while iterating.

## Releases

There is no version number stored in the repo. `versionCode` comes from the CI run number
and `versionName` from the tag the release workflow was dispatched against, both passed to
Gradle as `-P` flags — so a release is cut by tagging, never by editing a build file.

Building and signing happens only in [`release.yml`](.github/workflows/release.yml), which
is dispatched manually against a tag, gated on lint and unit tests passing for that exact
commit, and on approval from the `beta-signing` / `production-signing` environments. The
same tag can be released as `beta` first and promoted to `stable` later. The `make`
targets start that run; they never sign anything locally:

```bash
make version                     # current tag, next patch/minor/major, release state
make tag-patch                   # create the next tag locally (also: tag-minor, tag-major)
make tag-push                    # push it
make release-beta   TAG=1.3.1    # dispatch the signed release workflow for that tag
make release-stable TAG=1.3.1    # …or promote it to stable
make release-status              # recent release runs
```

## Reporting issues

File issues on [GitHub Issues](https://github.com/themadorg/bedrud-android/issues) with:

- Steps to reproduce
- Expected vs actual behavior
- App version (Settings → About), Android version, and device

Issues about the server, web app, or other clients belong on the
[main Bedrud repository](https://github.com/themadorg/bedrud/issues) instead.

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE).
