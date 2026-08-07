import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    namespace = "com.bedrud.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bedrud.app"
        minSdk = 28
        targetSdk = 37
        // Both dev and real release builds get an ever-increasing versionCode straight from
        // CI - dev from pr-build.yml's Actions run number (devVersionCode), real releases
        // from release.yml's (releaseVersionCode) - so neither ever needs a manual bump,
        // and re-dispatching a tag under stable after beta always yields a strictly higher
        // versionCode than the beta build before it (so a beta tester can update straight
        // to stable). Only a plain local/debug build falls back to the hardcoded default.
        versionCode = (project.findProperty("devVersionCode") as String?)?.toIntOrNull()
            ?: (project.findProperty("releaseVersionCode") as String?)?.toIntOrNull()
            ?: 1
        // Three-tier version-name strategy:
        //   dev     -> "<version>-dev"  (internal/PR builds; "-dev" added by the `dev`
        //              build type's versionNameSuffix below)
        //   beta    -> "<version>-beta" (release.yml dispatched with releaseChannel=beta)
        //   stable  -> "<version>"      (release.yml dispatched with releaseChannel=stable)
        // release.yml passes releaseVersionName=<the dispatched tag>, so the on-device
        // version string always matches the tag it was built from instead of a separately
        // hand-maintained value.
        //
        // Only a local build reaches the fallback, and it deliberately is not a real
        // version number. It used to be one ("1.2.0"), which drifted the moment 1.3.0 was
        // tagged: nothing reads it, so nothing catches it going stale, and meanwhile every
        // locally built APK claimed to be a release it wasn't. "0.0.0-local" cannot drift
        // and says what the build actually is - Settings > About shows this string.
        versionName = ((project.findProperty("releaseVersionName") as String?) ?: "0.0.0-local") +
            if (project.findProperty("releaseChannel") == "beta") "-beta" else ""

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resValue("string", "app_name", "Bedrud")

        // Pre-selected public server offered on first launch. A build constant (not a magic
        // string in the UI) so a dev/staging build can point elsewhere without touching code.
        // Override at build time with -PdefaultServerHost=... (e.g. to point a build at a
        // staging instance) instead of editing the Add Instance screen's default directly.
        val defaultServerHost = project.findProperty("defaultServerHost") ?: "bedrud.xyz"
        buildConfigField("String", "DEFAULT_SERVER_HOST", "\"$defaultServerHost\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
        // Dedicated key for dev/PR test builds only - separate from the real release key
        // above, so CI never needs access to production signing material. Read from env
        // vars (set by CI, or by a developer locally) rather than a committed file.
        create("dev") {
            val devKeystoreFile = rootProject.file(System.getenv("DEV_KEYSTORE_PATH") ?: "dev-release.jks")
            if (devKeystoreFile.exists()) {
                storeFile = devKeystoreFile
                storePassword = System.getenv("DEV_KEYSTORE_PASSWORD") ?: ""
                keyAlias = "bedrud-dev"
                keyPassword = System.getenv("DEV_KEYSTORE_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        // Dev-only UI affordances ("coming soon" hints, debug captions) are gated on this flag:
        // debug + dev builds show them, release (beta/stable) hides them. The `dev` build type
        // inherits this value from debug via initWith below, so setting it here covers both.
        getByName("debug") {
            buildConfigField("boolean", "DEV_HINTS", "true")
        }
        release {
            buildConfigField("boolean", "DEV_HINTS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Built on every PR so reviewers have a real APK to install and test.
        // Own applicationId (".dev" suffix) so it installs side-by-side with a real
        // release build on the same device instead of colliding with it.
        create("dev") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // Use the dedicated dev key only when it's actually available (CI, via env). Locally the
            // keystore is absent, so keep the debug signing inherited from initWith(debug) — that
            // keeps `installDev` installable side-by-side with a stable build, no CI secrets needed.
            val devKeystoreFile = rootProject.file(System.getenv("DEV_KEYSTORE_PATH") ?: "dev-release.jks")
            if (devKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("dev")
            }
            matchingFallbacks += listOf("debug")
            // Distinct home-screen name so a dev test build is never mistaken for the
            // real app when both are installed on the same device.
            resValue("string", "app_name", "Bedrud Dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    androidResources {
        // Ship only the languages the app itself is translated into (values-*/ below plus the
        // untranslated default). Without this, every transitive dependency - play-services,
        // credentials, androidx - drags in its own ~80 locales, none of which the app can
        // pair with a matching UI: a device set to, say, Italian already sees an all-English
        // Bedrud, so an Italian passkey dialog in the middle of it is inconsistent, not
        // helpful. Keep this list in sync when adding a new values-<tag>/ directory,
        // otherwise the new translation is silently dropped from the APK.
        localeFilters += listOf("en", "fa", "ar", "de", "es", "fr", "ja", "ru", "tr", "zh")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
}

// Default output names are derived from the Gradle module name (":app"), e.g.
// app-arm64-v8a-release.apk. Rebuild as bedrud-<abi>.apk for the release build type
// (used for both the beta and stable channels - a bare "bedrud" name is the one users
// actually download) and bedrud-dev-<abi>.apk for dev/PR test builds, so a dev test
// build can never be mistaken for - or silently overwrite - a real release download
// sharing the same filename. Plain "debug" is left alone - it's a local/CI convenience
// build, and the main bedrud repo's docs/CI already hardcode its "app-*-debug.apk"
// output names in several places that don't need churn for this.
androidComponents {
    onVariants { variant ->
        if (variant.buildType == "debug") return@onVariants
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?: "universal"
            val prefix = if (variant.buildType == "release") "bedrud" else "bedrud-${variant.buildType}"
            output.outputFileName.set("$prefix-$abi.apk")
        }
    }
}

// Because minSdk is 28, AGP defaults to storing both the .so files and the dex uncompressed
// in the APK, on the assumption the APK is delivered as a Play App Bundle where the store
// handles compression on the wire. Bedrud ships raw APKs off a GitHub Release instead, so
// that default means users download ~19 MB of literally uncompressed payload. Turning legacy
// (compressed) packaging back on roughly halves every download - see the PR for measurements.
//
// The trade is install footprint, and it differs per entry:
//   jniLibs - close to free. The installer extracts the .so, so on-disk usage stays about
//             the same; only the bytes on the wire shrink.
//   dex     - a real trade. ART keeps the extracted dex in its vdex, so the install grows by
//             roughly the uncompressed dex size while the download shrinks by ~4.5 MB.
// For an app distributed by direct download to users on metered or slow connections, download
// size is the one the user actually pays for, so both are enabled.
//
// Applied per-variant rather than through the android.packaging {} DSL so it lands only on
// `release` - the build type behind both the beta and stable channels, i.e. the only APKs an
// actual user downloads. `debug` and `dev` keep AGP's default uncompressed packaging: both are
// throwaway test builds, and deflating ~90 MB of dex buys nothing there beyond slower builds.
androidComponents {
    onVariants { variant ->
        if (variant.buildType != "release") return@onVariants
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
        variant.packaging.dex.useLegacyPackaging.set(true)
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // LiveKit
    implementation("io.livekit:livekit-android:2.27.0")
    implementation("io.livekit:livekit-android-compose-components:2.4.0")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")

    // Koin
    val koinVersion = "4.2.2"
    implementation("io.insert-koin:koin-android:$koinVersion")
    implementation("io.insert-koin:koin-androidx-compose:$koinVersion")

    // Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0")

    // Credential Manager (Passkeys)
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.gms:play-services-fido:21.3.0")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Browser (CustomTabs for OAuth)
    implementation("androidx.browser:browser:1.10.0")

    // QR code scanning (add-server flow) -- pure on-device decode, no Play Services dependency
    // (Play Services' own code scanner needs its module fetched over network on first use, which
    // is unreliable on restricted networks -- see AddInstanceScreen.kt for what was tried first)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
    testImplementation("io.mockk:mockk:1.14.11")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
