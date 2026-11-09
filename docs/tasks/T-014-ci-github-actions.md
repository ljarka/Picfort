# T-014 — GitHub Actions CI

**Status:** done  
**Component:** CI/CD  
**Depends on:** —

## Summary

GitHub Actions workflows for the Android app and ImageFrameServer were added as part of the
initial project setup. CI is already running and covers all requirements below.

## What was implemented

### `.github/workflows/ci.yml`
- **Trigger:** push to `main`, pull requests (any branch)
- **Android job:** checkout → JDK 21 (Temurin) → Gradle wrapper download with 3-attempt retry →
  `./gradlew verifyPaparazziDebug lintDebug assembleDebug`
  (`verifyPaparazziDebug` runs all unit tests AND screenshot comparison in one command)
- **On failure:** uploads `app/build/reports/` and `app/build/paparazzi/failures/` as artifacts
- **Always:** uploads debug APK as artifact
- **Concurrency:** `cancel-in-progress: true` per branch — no queued runs on force-push

### Frame server job (matrix: Python 3.11 + 3.13)
- Runs `pip install -r requirements.txt` → `python -m unittest discover -s tests -t tests -v`
- Working directory: `ImageFrameServer/`

### `.github/workflows/release.yml`
- Triggered by version tags (`v*`)
- Builds signed APK and publishes as a GitHub Release

### `.github/dependabot.yml`
- Weekly Gradle updates
- Monthly GitHub Actions updates

## Acceptance criteria (all verified)

- [x] Both workflows pass on the `main` branch
- [x] PR fails if screenshot test regresses (Paparazzi diff uploaded on failure)
- [x] PR fails if Python unit tests fail
- [x] Android job completes within 30-minute timeout
- [x] Screenshot diffs are uploaded as artifacts when Paparazzi fails

## Notes

No work needed. If regressions appear in future, verify:
1. Paparazzi requires JDK 21 — already set.
2. Maven Central network timeouts from GitHub runners — GRADLE_OPTS retries already configured.
3. `verifyPaparazziDebug` must be re-run after `recordPaparazziDebug` on intentional UI changes.
