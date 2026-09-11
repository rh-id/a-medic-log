# Suggested Commands (Windows, Git Bash)

## Gradle (use `./gradlew` in Git Bash, `gradlew.bat` in cmd)
- `./gradlew build` — full build (debug+release, lint, `generateLicenseHtml`); first build needs network for POM/license resolution.
- `./gradlew assembleDebug` / `assembleRelease` — APK only (release unsigned unless `SIGNING_KEY` env set).
- `./gradlew connectedCheck` — REAL test suite (instrumented; needs device/emulator; CI matrix API 23/26/31/36).
- `./gradlew test` — unit tests (only template tests exist; near-useless).
- `./gradlew generateLicenseHtml` — regenerate `app/src/main/assets/licenses.html` after dependency changes; delete `app/build/license-pom-cache` to force POM refetch.
- `./gradlew clean` — deletes root build dir.

## Environment
- JDK 17 required; `local.properties` (gitignored) → SDK at `C:\Users\Ruby\AppData\Local\Android\Sdk`.
- git is standard on Windows here; nothing project-specific.

## Notes
- Builds fail offline (license generator fetches POMs) and on AGP lint errors (e.g. vector pathData leading zeros).
- Gradle wrapper checksum is pinned — don't bump wrapper without updating `distributionSha256Sum`.
