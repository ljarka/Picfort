plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.github.ljarka.picfort"
    // 37 is required by current AndroidX/Compose/OkHttp; targetSdk stays 35 (no behaviour changes).
    compileSdk = 37

    defaultConfig {
        // Kept from before the rename: changing it installs a separate app and
        // existing installs would lose their login, settings and cache.
        applicationId = "com.github.ljarka.immich.android2"
        minSdk = 26
        targetSdk = 35
        // Set by the release workflow from the git tag; local builds are 1 / "dev".
        versionCode = System.getenv("PICFORT_VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("PICFORT_VERSION_NAME") ?: "dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from the environment (GitHub secrets in the release
    // workflow). Without it, release builds fall back to the debug key so they
    // still build locally.
    val releaseKeystore = System.getenv("PICFORT_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("PICFORT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PICFORT_KEY_ALIAS")
                keyPassword = System.getenv("PICFORT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
        // Release code (R8, no debug overhead) for the :benchmark module. Debug-signed so it
        // installs over a debug build and keeps its login.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    api(project(":core:model"))
    ksp(libs.hilt.android.compiler)
    ksp(libs.room.compiler)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.hilt.work)
    implementation(libs.work.runtime.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.error.prone.annotations)
    implementation(libs.compose.animation)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.hilt.android)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.security.crypto)
    implementation(libs.logging.interceptor)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material.icons.core)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.profileinstaller)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// AGP 9 builds Kotlin itself (no org.jetbrains.kotlin.android plugin).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
