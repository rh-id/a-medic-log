# Tech Stack

## Language / build
- Pure Java 17 (no Kotlin), Groovy-DSL Gradle (not .kts). AGP 9.2.1 via old `buildscript` classpath (root `build.gradle`), Gradle 9.4.1 wrapper — `distributionSha256Sum` is PINNED in `gradle/wrapper/gradle-wrapper.properties` (wrapper upgrade must update it or builds fail).
- `minSdk 21`, `targetSdk 37`, `compileSdk 37`. `coreLibraryDesugaring` (desugar_jdk_libs 2.1.5) in both modules; `vectorDrawables.useSupportLibrary true`; base has `multiDexEnabled true`.
- `gradle.properties` minimal: `-Xmx2048m`, `android.useAndroidX=true`. No jetifier, no caching flags.
- Repos declared ONLY in `settings.gradle` (google, mavenCentral, jitpack) with `FAIL_ON_PROJECT_REPOS`.

## Key libraries
- Room 2.7.2 (`room-runtime` is `api` in base; `annotationProcessor room-compiler`).
- RxJava 3.1.12 + RxAndroid 3.0.2; WorkManager 2.10.5 (on-demand init — `InitializationProvider` removed in manifest, config from `MainApplication`).
- Author libs (JitPack `com.github.rh-id`): a-provider v0.0.23, a-navigator v0.0.71 + a-navigator-extension-dialog v0.0.71, a-logger v0.0.3, `co.rh.id.lib:rx-utils` v0.0.3, `co.rh.id.lib:concurrent-utils` v0.0.3 (WeightedThreadPool), a-poi-spreadsheet v0.1.0 (XLSX export, bundles POI fork — pulls META-INF/DEPENDENCIES pickFirst).
- UI: material 1.13.0, constraintlayout, recyclerview, drawerlayout, swiperefreshlayout, gridlayout, PhotoView 2.3.0 (chrisbanes JitPack), leakcanary plumber-android 2.14 (no-op-ish leak repair), exifinterface.

## Versions / ids
- `applicationId`/`namespace` = `m.co.rh.id.a_medic_log` (app) / `.base` (base).
- `versionCode`/`versionName` live ONLY in `app/build.gradle` `defaultConfig` (was 27 / "1.5.0").
- Release build: `minifyEnabled false` (proguard files declared but inert).

## Tests
- Unit tests: NONE real (template `ExampleUnitTest` only). JUnit 4.13.2.
- Real suite = instrumented androidTest: JUnit4 + `androidx.test.ext:junit`, espresso-core (declared, barely used), work-testing, room-testing (`MigrationTestHelper`). AndroidTest package is `m.co.rh.id.a_medic_log` (no `.app`).
