plugins {
    id("picfort.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.ljarka.picfort.core.database"
}

dependencies {
    api(project(":core:model"))

    ksp(libs.room.compiler)
    ksp(libs.hilt.android.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlin.serialization.json)

    testImplementation(libs.junit)
}
