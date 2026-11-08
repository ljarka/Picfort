# T-014 — GitHub Actions CI

**Status:** draft  
**Component:** CI/CD  
**Depends on:** —

## Summary

Add GitHub Actions workflows for the Android app and ImageFrameServer:
build on every PR, run tests, fail on screenshot regressions.

## Why

The repo already has screenshot tests (`verifyPaparazziDebug`) and Python unit tests.
Running them automatically on every PR prevents regressions from slipping in.

## Design notes (to be refined)

### Android workflow (`.github/workflows/android.yml`)
- Trigger: push to main, PRs
- Steps: checkout → setup JDK → `./gradlew testDebugUnitTest` → `./gradlew verifyPaparazziDebug`
- Artifact: upload screenshot diff on failure

### Python workflow (`.github/workflows/python.yml`)
- Trigger: push to main, PRs targeting `ImageFrameServer/`
- Steps: checkout → setup Python → `pip install -r requirements.txt` → `python3 -m unittest discover`

### Dependabot
Already configured (`.github/dependabot.yml`). Verify it covers both Gradle and pip.

## Acceptance criteria (to be defined during implementation)

- Both workflows pass on main branch
- PR fails if screenshot test regresses
- PR fails if Python unit tests fail
- Build time < 10 minutes
