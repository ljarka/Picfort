plugins {
    id("picfort.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.ljarka.picfort.core.network"
}

dependencies {
    api(project(":core:model"))

    ksp(libs.hilt.android.compiler)

    implementation(libs.hilt.android)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
}
