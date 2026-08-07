# Bedrud Android - developer shortcuts.
#
# This file never reimplements anything: it only launches gradlew, adb, git and gh.
# Two rules worth knowing before adding a target here:
#
#   1. `make check` must stay identical to what CI gates on (lint + unit tests, see
#      .github/workflows/pr-build.yml). If it drifts, "it passed locally" stops meaning
#      anything.
#   2. Nothing here writes a version into a file. The git tag is the only source of
#      truth - app/build.gradle.kts takes versionName/versionCode from -P flags that
#      release.yml passes in, so there is no in-repo number to bump. The version targets
#      below create tags; the release targets dispatch the workflow that builds them.
#
# Run `make help` for the target list.

# Recipes use POSIX shell throughout (test, case, command substitution). On Windows that
# means running make from Git Bash or WSL, not cmd/PowerShell - uname is the cheapest
# probe for "am I actually in a POSIX shell", since cmd has no such command.
ifeq ($(OS),Windows_NT)
    ifeq ($(shell uname -s),)
        $(error No POSIX shell found. On Windows run make from Git Bash or WSL. Note that make is not bundled with Git for Windows - install it with `winget install ezwinports.make` or `scoop install make`)
    endif
    # Absolute path on purpose. make skips the shell for recipe lines that contain no
    # metacharacters and execs them itself, and the two worlds disagree on how to spell a
    # program in the current directory: that direct path (Windows) rejects "./gradlew.bat",
    # while sh - which the same file's other recipes do go through - rejects ".\gradlew.bat".
    # An absolute path is the one spelling both accept, so $(GRADLEW) stays correct whether
    # or not a given line happens to reach sh.
    GRADLEW := $(CURDIR)/gradlew.bat
else
    GRADLEW := ./gradlew
endif

APP_ID           := com.bedrud.app
APP_ID_DEV       := com.bedrud.app.dev
MAIN_ACTIVITY    := $(APP_ID)/.MainActivity
RELEASE_WORKFLOW := release.yml

# Optional: `make build-dev SERVER=staging.example.com` bakes a different default server
# host into BuildConfig.DEFAULT_SERVER_HOST. Omitted -> the app's own default (bedrud.xyz).
GRADLE_ARGS := $(if $(SERVER),-PdefaultServerHost=$(SERVER))

# Only plain semver tags are releases (1.2.0, 1.3.0). Anything else is ignored, so a
# stray tag can never be mistaken for the current version.
TAG_GLOB := [0-9]*.[0-9]*.[0-9]*

.DEFAULT_GOAL := help
.PHONY: help doctor build build-dev build-release install install-dev run \
        uninstall uninstall-dev logcat lint test check clean version \
        tag-patch tag-minor tag-major tag-push release-beta release-stable \
        release-status bump dispatch

## --- Environment ---------------------------------------------------------------

help: ## Show this help
	@echo "Bedrud Android - make targets"
	@awk 'BEGIN { FS = ":.*## " } \
		/^## --- / { sub(/^## --- /, ""); sub(/ *-+$$/, ""); printf "\n  %s\n", $$0; next } \
		/^[a-zA-Z0-9_-]+:.*## / { printf "    %-16s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@echo ""
	@echo "  Variables"
	@echo "    SERVER=<host>      default server host baked into a build"
	@echo "    TAG=<version>      tag the release-* targets act on"
	@echo ""

# On Windows, Android Studio writes sdk.dir escaped Java-properties style
# (sdk.dir=C\:\\Users\\...), so it has to be un-escaped before [ -d ] can test it. That
# needs a literal backslash in the sed script, and the usual way of spelling one there
# does not survive make on Windows - the bracket expression below is the spelling that
# works under both make and every sed. On Unix the path has no backslashes and all three
# substitutions are no-ops.
doctor: ## Check the local toolchain (JDK, SDK, adb, git, gh, signing)
	@fail=0; \
	java_bin="java"; [ -n "$$JAVA_HOME" ] && java_bin="$$JAVA_HOME/bin/java"; \
	if ! "$$java_bin" -version >/dev/null 2>&1; then \
		echo "  FAIL  java not found. Install JDK 17, or set JAVA_HOME (Android Studio ships one in its jbr/ directory)."; \
		fail=1; \
	else \
		v=$$("$$java_bin" -version 2>&1 | head -1 | sed 's/[^"]*"\([0-9]*\).*/\1/'); \
		if [ "$$v" = "17" ]; then echo "  ok    JDK $$v"; \
		else echo "  WARN  JDK $$v - the project targets 17 (jvmToolchain(17) in app/build.gradle.kts)"; fi; \
	fi; \
	sdk="$$ANDROID_HOME"; [ -z "$$sdk" ] && sdk="$$ANDROID_SDK_ROOT"; \
	if [ -z "$$sdk" ] && [ -f local.properties ]; then \
		sdk=$$(sed -n 's/^sdk\.dir=//p' local.properties | head -1 | tr -d '\r' \
			| sed -e 's|[\]|/|g' -e 's|//|/|g' -e 's|/:|:|'); \
	fi; \
	if [ -n "$$sdk" ] && [ -d "$$sdk" ]; then echo "  ok    Android SDK  $$sdk"; \
	else echo "  FAIL  Android SDK not found. Set ANDROID_HOME, or let Android Studio write sdk.dir into local.properties."; fail=1; fi; \
	if command -v adb >/dev/null 2>&1; then \
		n=$$(adb devices | awk 'NR > 1 && $$2 == "device"' | wc -l | tr -d ' '); \
		echo "  ok    adb          $$n device(s) attached"; \
	else echo "  WARN  adb not on PATH - install/run/logcat need it (it lives in <sdk>/platform-tools)"; fi; \
	if command -v git >/dev/null 2>&1; then echo "  ok    git"; else echo "  FAIL  git not found"; fail=1; fi; \
	if command -v gh >/dev/null 2>&1; then \
		if gh auth status >/dev/null 2>&1; then echo "  ok    gh           authenticated"; \
		else echo "  WARN  gh present but not authenticated - run 'gh auth login' before the release-* targets"; fi; \
	else echo "  WARN  gh not found - the release-* targets need it (https://cli.github.com)"; fi; \
	if [ -f keystore.properties ]; then echo "  ok    keystore.properties present - build-release will be signed"; \
	else echo "  note  keystore.properties absent - build-release produces an UNSIGNED APK (fine for local testing)"; fi; \
	exit $$fail

## --- Build ---------------------------------------------------------------------

build: ## Debug APKs
	$(GRADLEW) assembleDebug $(GRADLE_ARGS)

# The help text below is read straight out of this file by awk, so it cannot use
# $(APP_ID_DEV) - it would print unexpanded.
build-dev: ## Dev APKs - install alongside the real app (com.bedrud.app.dev)
	$(GRADLEW) assembleDev $(GRADLE_ARGS)

build-release: ## Release APKs (needs keystore.properties, otherwise unsigned)
	@[ -f keystore.properties ] || echo "  note: keystore.properties absent - this build will NOT be release-signed, so it cannot install as an update over a real release."
	$(GRADLEW) assembleRelease $(GRADLE_ARGS)

## --- Device --------------------------------------------------------------------

# Gradle's install* tasks rather than `adb install <path>`: they pick the split APK
# matching the attached device's ABI, and keep output filenames out of this file.
install: ## Build + install the debug app
	$(GRADLEW) installDebug $(GRADLE_ARGS)

install-dev: ## Build + install the dev app (side by side with the real one)
	$(GRADLEW) installDev $(GRADLE_ARGS)

run: install ## Install the debug app and launch it
	adb shell am start -n $(MAIN_ACTIVITY)

uninstall: ## Remove the debug/release app from the device
	adb uninstall $(APP_ID)

uninstall-dev: ## Remove the dev app from the device
	adb uninstall $(APP_ID_DEV)

logcat: ## Tail logcat for this app only
	@pid=$$(adb shell pidof $(APP_ID) 2>/dev/null | tr -d '\r'); \
	[ -n "$$pid" ] || { echo "$(APP_ID) is not running - start it first (make run)."; exit 1; }; \
	adb logcat --pid="$$pid"

## --- Quality -------------------------------------------------------------------

# CI passes --no-daemon; that is for throwaway runners and only slows a dev machine down,
# so it is deliberately not mirrored here. The tasks themselves are identical.
lint: ## Android lint - the task CI runs
	$(GRADLEW) lint

test: ## Unit tests - the task CI runs
	$(GRADLEW) testDebugUnitTest

check: lint test ## lint + test: everything pr-build.yml gates a PR on

clean: ## Delete build outputs
	$(GRADLEW) clean

## --- Version & release ---------------------------------------------------------

version: ## Show the current tag, the next versions, and GitHub release state
	@cur=$$(git tag -l '$(TAG_GLOB)' --sort=-v:refname | head -1); \
	[ -n "$$cur" ] || { echo "No release tag yet."; exit 0; }; \
	maj=$${cur%%.*}; rest=$${cur#*.}; min=$${rest%%.*}; pat=$${rest#*.}; \
	echo "  current tag       $$cur"; \
	echo "  make tag-patch -> $$maj.$$min.$$((pat + 1))"; \
	echo "  make tag-minor -> $$maj.$$((min + 1)).0"; \
	echo "  make tag-major -> $$((maj + 1)).0.0"; \
	if command -v gh >/dev/null 2>&1; then echo ""; gh release list --limit 5 2>/dev/null || true; fi

# A bump creates a tag and stops. Pushing is a separate, deliberate step (tag-push):
# a pushed tag is what release.yml gets dispatched against, so it is effectively permanent.
#
# The three targets share one recipe via a target-specific PART, which prerequisites
# inherit. Recursive make would work too, but every rejected guard below would then print
# a second "make[1]: *** Error" line on top of its own message.
tag-patch: PART := patch
tag-minor: PART := minor
tag-major: PART := major

tag-patch: bump ## Tag the next patch version (x.y.Z+1)
tag-minor: bump ## Tag the next minor version (x.Y+1.0)
tag-major: bump ## Tag the next major version (X+1.0.0)

bump:
	@git diff-index --quiet HEAD -- || { echo "Working tree is dirty - commit or stash first."; exit 1; }
	@branch=$$(git rev-parse --abbrev-ref HEAD); \
	[ "$$branch" = "master" ] || { echo "On '$$branch' - releases are tagged from master."; exit 1; }
	@git fetch --quiet origin master
	@[ "$$(git rev-parse HEAD)" = "$$(git rev-parse origin/master)" ] || \
		{ echo "Local master differs from origin/master - sync first, so the tag matches what CI checks out."; exit 1; }
	@cur=$$(git tag -l '$(TAG_GLOB)' --sort=-v:refname | head -1); \
	[ -n "$$cur" ] || { echo "No existing $(TAG_GLOB) tag to bump from."; exit 1; }; \
	maj=$${cur%%.*}; rest=$${cur#*.}; min=$${rest%%.*}; pat=$${rest#*.}; \
	case "$(PART)" in \
		patch) new="$$maj.$$min.$$((pat + 1))" ;; \
		minor) new="$$maj.$$((min + 1)).0" ;; \
		major) new="$$((maj + 1)).0.0" ;; \
		*) echo "PART must be patch, minor or major."; exit 1 ;; \
	esac; \
	if git rev-parse -q --verify "refs/tags/$$new" >/dev/null; then echo "Tag $$new already exists."; exit 1; fi; \
	git tag -a "$$new" -m "$$new"; \
	echo "Tagged $$new (was $$cur), locally. Next:"; \
	echo "  make tag-push"; \
	echo "  make release-beta TAG=$$new"

tag-push: ## Push the newest local tag to origin
	@tag=$$(git tag -l '$(TAG_GLOB)' --sort=-v:refname | head -1); \
	[ -n "$$tag" ] || { echo "No tag to push."; exit 1; }; \
	git push origin "refs/tags/$$tag" && echo "Pushed $$tag."

# A release is a manual dispatch of release.yml against a tag, gated on the beta-signing /
# production-signing environments (required reviewers) and on lint+test passing for that
# exact tag. These targets only start that run - they never build or sign locally.
release-beta:   CHANNEL := beta
release-stable: CHANNEL := stable

release-beta:   dispatch ## Dispatch a beta release for TAG=<version>
release-stable: dispatch ## Dispatch a stable release for TAG=<version>

dispatch:
	@[ -n "$(TAG)" ] || { echo "Usage: make release-$(CHANNEL) TAG=<version>   (see: make version)"; exit 1; }
	@command -v gh >/dev/null 2>&1 || { echo "gh is required: https://cli.github.com"; exit 1; }
	@git ls-remote --exit-code --tags origin "refs/tags/$(TAG)" >/dev/null 2>&1 || \
		{ echo "Tag $(TAG) is not on origin - push it first (make tag-push)."; exit 1; }
	gh workflow run $(RELEASE_WORKFLOW) --ref "$(TAG)" -f channel=$(CHANNEL)
	@echo "Dispatched a $(CHANNEL) release for $(TAG). It waits on reviewer approval; follow it with: make release-status"

release-status: ## Recent release workflow runs
	@gh run list --workflow=$(RELEASE_WORKFLOW) --limit 10
