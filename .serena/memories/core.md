# a-medic-log — Core Map

Personal medical-notes Android app (Java, GPL-3.0). Demo/production app for author's JitPack libs: a-navigator (navigation), a-provider (DI), a-logger, rx-utils, concurrent-utils, a-poi-spreadsheet (`com.github.rh-id:*`).

## Modules (namespaces)
- `app` `m.co.rh.id.a_medic_log` — UI, navigation, DI wiring, business logic, notifications, export/import.
- `base` `m.co.rh.id.a_medic_log.base` — data layer only: Room entities/DAOs/repositories, domain state wrappers.

## app package map (`app/src/main/java/m/co/rh/id/a_medic_log/app/`)
- root: `MainApplication` (providers, WorkManager on-demand init, crash logger), `MainActivity` (single-activity container)
- `constants/`: `Routes` (string route names), `Constants` (FileProvider authority)
- `provider/`: `AppProviderModule` (app scope incl. navigator+route map), `CommandProviderModule` (31 commands), `RxProviderModule` (`RxDisposer`), `StatefulViewProvider(+Module)` (view scope)
- `provider/command/`: `*Cmd` business logic + validation
- `provider/notifier/`: 7 `*ChangeNotifier` event buses
- `provider/component/`: notification handling, reminder event handler, exporters, prefs, file cleanup
- `ui/page/`: full-screen pages (17); `ui/component/`: reusable StatefulView widgets (~29, incl. `settings/LicensesPage`, `LogPage`)
- `workmanager/` (`MedicineReminderNotificationWorker`), `receiver/` (notification-action receivers), `rx/` (`RxDisposer`, `RxUtils`), `util/`

## base package map (`base/src/main/java/m/co/rh.id/a_medic_log/base/`)
- `AppDatabase` (v5), `BaseApplication` (static `of(context)` accessor)
- `entity/` + `dao/` (9+9), `repository/` (5), `state/` (`*State` Serializable wrappers), `room/DbMigration`, `room/converter/`, `provider/` (`BaseProviderModule`, `DatabaseProviderModule`, `FileHelper`, `ImageHelper`)

## Project-wide invariants
- Single `MainActivity`; every page is a `StatefulView`; navigation via string routes.
- DI = a-provider service locator by Class key (no Dagger/Hilt); deps surface as `ProviderValue<T>` (`lazyGet`, unwrap `.get()`).
- All business logic through `*Cmd` commands run on a shared `ExecutorService`; every mutation fires a `*ChangeNotifier`; UI re-renders from notifier flows.
- Every Rx subscription must be registered in `RxDisposer` or it leaks.
- Min SDK 21 with coreLibraryDesugaring; no Kotlin anywhere.

## Detailed memories
- Layer/scopes/command/reminder/export internals + how to add an entity or page: `mem:architecture`
- Languages, versions, libraries, test frameworks: `mem:tech_stack`
- Versioning, release checklist, signing, CI workflows, license-generation machinery: `mem:build-release`
- Code style, naming, Rx rules, resource/i18n/commit conventions: `mem:conventions`
- Dev/build/test commands (Windows Git Bash): `mem:suggested_commands`
- What must pass/be updated before finishing a task: `mem:task_completion`
