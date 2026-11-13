plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.paparazzi) apply false
}

/**
 * KSP2 runs inside the Gradle daemon (Worker API), so the KSP tasks of several modules can run at
 * the same time even without --parallel. When they did, one module sometimes came out with no
 * generated Java at all (no Hilt modules) and `:app:hiltJavaCompile*` failed with a random
 * `[Dagger/MissingBinding]` (release CI v0.8.0, v0.8.2; locally too). One KSP task at a time;
 * everything else still runs alongside. See docs/decisions/0012-serial-ksp.md.
 */
abstract class KspLock : BuildService<BuildServiceParameters.None>

val kspLock = gradle.sharedServices.registerIfAbsent("kspLock", KspLock::class) {
    maxParallelUsages.set(1)
}

/** Hilt annotations whose KSP output always includes `hilt_aggregated_deps`. */
val hiltAggregating = Regex("@(InstallIn|HiltViewModel|AndroidEntryPoint|HiltAndroidApp|EntryPoint)\\b")

subprojects {
    plugins.withId("com.google.devtools.ksp") {
        tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
            usesService(kspLock)
        }
    }
    plugins.withId("com.google.dagger.hilt.android") {
        val sources = layout.projectDirectory.dir("src/main/java").asFile
        val generated = layout.buildDirectory.dir("generated/ksp")
        tasks.matching { it.name.matches(Regex("ksp(Debug|Release)Kotlin")) }.configureEach {
            val variant = name.removePrefix("ksp").removeSuffix("Kotlin").replaceFirstChar { it.lowercase() }
            val taskPath = path
            doLast {
                // Fail here, naming the module, instead of with a misleading MissingBinding in :app.
                val usesHilt = sources.walkTopDown().any { it.extension == "kt" && hiltAggregating.containsMatchIn(it.readText()) }
                val deps = generated.get().dir("$variant/java/hilt_aggregated_deps").asFile
                if (usesHilt && (deps.listFiles()?.isEmpty() != false)) {
                    throw GradleException("$taskPath generated no Hilt code (KSP output is incomplete). Run the build again.")
                }
            }
        }
    }
}
