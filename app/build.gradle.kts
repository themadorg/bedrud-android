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
        // hand-maintained value. A local/default build falls back to the placeholder below.
        versionName = ((project.findProperty("releaseVersionName") as String?) ?: "1.2.0") +
            if (project.findProperty("releaseChannel") == "beta") "-beta" else ""

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resValue("string", "app_name", "Bedrud")
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
                // Alias is the label baked into the physical keystore (historically
                // "bedrud-qa"); it identifies the key entry inside the .jks and is
                // independent of the build-track name.
                keyAlias = "bedrud-qa"
                keyPassword = System.getenv("DEV_KEYSTORE_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
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
            signingConfig = signingConfigs.getByName("dev")
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

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
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
