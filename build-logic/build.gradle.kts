plugins {
    `kotlin-dsl`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:9.5.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
}
