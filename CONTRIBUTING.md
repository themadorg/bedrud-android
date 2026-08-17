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
See [README.md](README.md#building-from-source) for build flags such as
`-PdefaultServerHost`, and for release signing.

## Project structure

Single `:app` module. The source layout is in [README.md](README.md#architecture), and
the conventions that matter when changing it — design tokens, the multi-instance model,
navigation, networking — are in [AGENTS.md](AGENTS.md). The design system itself is
documented in [DESIGN.md](DESIGN.md).

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

Tests are JUnit 4 with MockK, OkHttp MockWebServer, and kotlinx-coroutines-test. There is
no instrumented test directory. `InMemorySharedPreferences` in `testutil/` lets you inject
into anything taking `SharedPreferences` without pulling in the Android framework.

## Releases

There is no version number stored in the repo. `versionCode` comes from the CI run number
and `versionName` from the tag the release workflow was dispatched against, both passed to
Gradle as `-P` flags — so a release is cut by tagging, never by editing a build file.

Building and signing happens only in [`release.yml`](.github/workflows/release.yml), which
is dispatched manually against a tag, gated on lint and unit tests passing for that exact
commit, and on approval from the `beta-signing` / `production-signing` environments. The
same tag can be released as `beta` first and promoted to `stable` later.

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
