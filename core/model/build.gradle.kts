plugins {
    id("picfort.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.ljarka.picfort.core.model"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.graphics)
    implementation(libs.kotlin.serialization.json)
    testImplementation(libs.junit)
}
