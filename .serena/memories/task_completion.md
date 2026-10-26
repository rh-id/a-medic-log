# Task Completion Checklist

1. `./gradlew build` must pass — this runs AGP lint (default config, failures block build) and `generateLicenseHtml` (network on first run).
2. Added/changed dependencies? → `licenses.html` regenerates on build: COMMIT the regenerated `app/src/main/assets/licenses.html`; if build fails on missing/wrong metadata, add override in `app/licenses.yml`.
3. Touched strings/UI text? → add the string to ALL 10 locale `values*` dirs (parity is exact; missing key = MissingTranslation lint ERROR = build failure); Indonesian is `values-in`.
4. Touched DB (entity/column)? → bump `AppDatabase` version + add raw-SQL migration in `base/room/DbMigration.java` + commit new `base/schemas/.../<N>.json` (`DbMigrationTest` needs it).
5. Tests: `./gradlew :app:testDebugUnitTest` (62 unit tests — permission gate, calculators, reminder event handler, delete-scope builder) + `./gradlew connectedCheck` (instrumented, BOTH modules — app: export/import round-trip, exporters; base: MedicineRepositoryTest 19 incl. multi-threaded race tests, DAO + migration tests; needs emulator, CI matrix API 23/26/29/31/36; ExcelExporterTest auto-skips below API 26).
6. New page/feature wiring (routes, provider registrations, RxDisposer) can only fail at RUNTIME — re-read `mem:architecture` checklist; consider running the app on an emulator.
7. Version bump / release flow → see `mem:build-release` (10-locale changelogs mandatory, en-US one feeds the GitHub release body).
8. No formatter/linter to run — hand-match style in `mem:conventions`.
