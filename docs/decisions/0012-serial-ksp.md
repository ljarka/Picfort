# 0012 — One KSP task at a time

**Status:** accepted (owner request 2026-11-13)  
**Deciders:** owner, Claude  
**Date:** 2026-11-13  

## Context

Release builds failed now and then in `:app:hiltJavaCompileRelease` with `[Dagger/MissingBinding]`
for classes the release didn't touch (`FrameSender` in v0.8.0, `FamilyMemberProvider` and three
more in v0.8.2); re-running the job passed. Locally the same happened (`SyncStore`, `SyncApi`) and
needed `--rerun-tasks`.

The CI log showed the cause: in the failing run `:feature:family:compileReleaseJavaWithJavac` was
`NO-SOURCE`, so KSP had generated no Java for that module (Hilt modules, `hilt_aggregated_deps`);
in the passing re-run it had. KSP 2.3 (KSP2) runs in the Gradle daemon through the Worker API, so
the KSP tasks of many modules run at the same time even without `--parallel`: measured locally,
up to 10 of 18 at once. Now and then one of them came out empty.

## Decision

- The root `build.gradle.kts` registers a `kspLock` build service with `maxParallelUsages = 1`;
  every `ksp*Kotlin` task uses it. KSP tasks run one after another (measured: at most 1 at once);
  compilation, resources and tests still run alongside. A clean `verifyPaparazziDebug
  assembleRelease` takes about 1 min 12 s locally.
- After `kspDebugKotlin` / `kspReleaseKotlin` of a Hilt module whose sources use `@InstallIn`,
  `@HiltViewModel`, `@AndroidEntryPoint`, `@HiltAndroidApp` or `@EntryPoint`, the build checks that
  `hilt_aggregated_deps` was generated, and otherwise fails right there, naming the module.

## Consequences

- Release CI should no longer need re-runs for this. If the check ever fires, the race isn't the
  only cause: report it upstream (KSP) with the module named in the error.
- The lock can go once KSP2 is safe to run concurrently in one daemon; the check can stay.
