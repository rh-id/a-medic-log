# Build / CI / Release

## Versioning & release checklist
- versionCode/versionName ONLY in `app/build.gradle` `defaultConfig`.
- Release steps: bump version → create `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` in ALL 10 locales (de-DE, en-US, et, fr-FR, id, is-IS, it-IT, nb-NO, nn-NO, rm) → conventional commit → push tag `v<versionName>` (e.g. `v1.5.0`).
- TRAP: gradle `afterEvaluate` hook (app/build.gradle) copies `en-US/changelogs/<versionCode>.txt` → `app/build/changelog.txt` ONLY if it exists — missing file silently reuses the PREVIOUS version's changelog as the GitHub Release body.
- android-release.yml (tag `v*`) runs `./gradlew build` and publishes debug + release APKs + changelog as release body via softprops/action-gh-release.

## Signing
- Configuration-time env vars: `SIGNING_KEY` (base64 keystore), `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`. Absent `SIGNING_KEY` ⇒ unsigned release APK. No keystore.properties support.

## CI (.github/workflows/)
- `gradlew-build.yml` "Android CI": push/PR master → `./gradlew build` (JDK 17).
- `android-emulator-test.yml`: push/PR master → `./gradlew connectedCheck` on emulator matrix API 23/26/31/36, fail-fast false; AVD snapshot with raised heap; this workflow pins actions by SHA (others by tag).
- `android-release.yml`: tag `v*` → signed release (env-var signing path).

## Licenses page (build-time, breaks builds)
- `gradle/license-html-generator.gradle` (~2000 lines, mostly embedded license texts): task `generateLicenseHtml` resolves external deps' POMs (cache `app/build/license-pom-cache` → Gradle cache → HTTP fetch) → writes `app/src/main/assets/licenses.html`.
- Wired into `preBuild` + cross-project mergeAssets deps ⇒ runs on EVERY build; first build needs network; missing POM/metadata = GradleException (build fails).
- Overrides in `app/licenses.yml` — hand-rolled line parser (NOT yaml lib): `- artifact: group:name[:version]` (wildcard `+` allowed) + 2-space-indented `name/copyrightHolder/license/licenseUrl/url`.
- `licenses.html` is COMMITTED (linguist-generated in .gitattributes): dependency change ⇒ regenerate and COMMIT it; manual edits get overwritten. Stale POM cache keyed by coordinates — delete `app/build/license-pom-cache` to force refetch.

## Changelog plumbing
- app/build.gradle hook reads fastlane en-US changelog; release body comes from `app/build/changelog.txt`. fastlane is metadata-only (no Fastfile): `fastlane/metadata/android/<locale>/` per locale holds `title.txt`, `short_description.txt`, `full_description.txt`, `images/` (featureGraphic, icon, phoneScreenshots 1-3), `changelogs/`. Store listing text is per-locale too — sync listing copy across all 10 locales when changing it. `graphics/` = SVG design sources for launcher/feature graphics.

## Misc gotchas
- Wrapper `distributionSha256Sum` pinned (gradle 9.4.1).
- JitPack required for all `com.github.*` deps; repos centralized in settings.gradle.
- No lint config anywhere — AGP lint at DEFAULTS still fails builds (e.g. InvalidVectorPath on vector pathData without leading zeros).
- `local.properties` (gitignored) points to local SDK.
