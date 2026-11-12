plugins {
    id("picfort.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.github.ljarka.picfort.core.ui"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":core:model"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material.icons.core)
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
