# Architecture (a-navigator + a-provider + Rx)

## DI scopes (a-provider, Class-key service locator, NO annotations)
- App scope: `AppProviderModule` registers `BaseProviderModule` + `DatabaseProviderModule` + `CommandProviderModule`, async: WorkManager, prefs, 7 notifiers, FileCleanUpTask, MedicineReminderEventHandler; builds `INavigator`/`Navigator` with route map; `registerPool(StatefulViewProvider.class)`.
- Activity scope: `MainActivity.onCreate` creates nested provider named `"ActivityProvider"` with `RxProviderModule` (→ activity `RxDisposer`).
- View scope: `"StatefulViewProvider"` nested provider = `RxProviderModule` + `CommandProviderModule`; pages get it via `provider.get(StatefulViewProvider.class)` (see `BaseDetailPage`).
- Resolution returns `ProviderValue<T>` from `provider.get()/lazyGet(Class)` — unwrap with `.get()`; commands cache `ProviderValue` fields.
- base: `BaseProviderModule` = WeightedThreadPool (maxWeight 5, concurrent-utils), single-thread ScheduledExecutorService, main Handler, `CompositeLogger` (Android+File+Toast), FileHelper. `DatabaseProviderModule` = Room + each of 9 DAOs registered individually (deliberate, decoupled from AppDatabase) + 5 repositories lazy.

## Navigation
- Routes = plain strings in `app/constants/Routes.java` (`"splash"`, `"/"` = home, `/settings`, `/profiles*`, `/notes*`, `/medicines*`, `/medicineIntakes*`, `/common/*Dialog`); registered in navMap inside `AppProviderModule.getNavigator()` as `(args, activity) -> new XxxPage()` lambdas + merged `NavExtDialogConfig`.
- EXCEPTION: `LicensesPage`, `LogPage` pushed with inline anonymous factories — grep `mNavigator.push((args` before assuming a page is in `Routes`.
- `MainActivity`: back → `navigator.onBackPressed()`; `onActivityResult` forwarded; declares ALL `configChanges` — night-mode/locale change triggers debounced (100ms BehaviorSubject) `navigator.reBuildAllRoute()`, NOT activity recreation. `processNotification(intent)` on create + `onNewIntent`.
- `SplashPage` replaces to next route after 1000ms; `HomePage` = DrawerLayout root + quick-add.
- Page args: nested `public static class Args implements Serializable` with `Args.withX(...)` factories; results `Page.Result.of(navRoute)`.
- Child StatefulViews: `@NavInject`, disposed+null'ed in `dispose(Activity)`.

## Command pattern (business logic + validation, no base class)
- 31 `*Cmd` in `app/provider/command/`: `New/Update/Delete/Query(+Paged)` × entity + export/import trio. Registered lazy in `CommandProviderModule`.
- Execution idiom: `Single.fromCallable(() -> { repo.insert(x); notifier.xAdded(x); return x; }).subscribeOn(Schedulers.from(mExecutorService.get()))` — every mutation fires its notifier.
- Commands double as validators: `valid(state)` pushes errors into BehaviorSubjects exposed as `getXxxValid(): Flowable<String>` — UI subscribes to per-field error streams on the command instance.

## Notifiers (event bus)
- 7 `*ChangeNotifier` in `app/provider/notifier/` — NO base class, each hand-rolls `PublishSubject.toSerialized()` exposed as `Flowable` (BackpressureStrategy.BUFFER) for added/updated/deleted. `NoteChangeNotifier.NoteUpdatedEvent` carries before/after `NoteState`.
- Key consumer: `MedicineReminderEventHandler` — reschedules WorkManager on any reminder-affecting change.

## Reminders / notifications
- `MedicineReminderNotificationWorker`: one-time work (input `Keys.LONG_MEDICINE_REMINDER_ID`), checks `reminderDays.contains(today)`, posts notification, re-enqueues next day; unique work tag `MEDICINE_REMINDER_TAG_<id>`; also cancels legacy periodic work.
- Receivers (not exported): TakeMedicine (logs intake), DisableMedicineReminder, Delete — tracked via `AndroidNotification` entity/repository (persisted request_id/group_key). Channel created lazily in `MedicineReminderNotificationBuilder`.

## Export / import
- `ExportArchiveCmd`: full backup ZIP — `export.json` manifest + `profiles/<id>.json` + raw attachment files; `exportTo(uri)` via SAF.
- `ImportArchiveCmd`: `peek(Uri)` → `List<FileProfile>` preview (drives `ImportProfileSelectPage`), then `importProfiles`.
- `ExportSpreadsheetCmd`: XLSX (`ExcelExporter`, a-poi-spreadsheet, 4 sheets) on API 26+, else ZIP of CSVs (`CsvArchiveExporter`, UTF-8 BOM, CRLF).

## base data layer
- `AppDatabase` v5, name `"a-medic-log.db"`; entities: Profile, Note, NoteTag, NoteAttachment, NoteAttachmentFile, Medicine, MedicineReminder, MedicineIntake, AndroidNotification. Hierarchy: Profile→Note→Medicine→(Reminder|Intake); Note→Attachments/Tags.
- `DbMigration` = raw SQL (1_2 note_tag, 2_3 attachment tables, 3_4 indexes, 4_5 full rebuild adding FK ON DELETE CASCADE). Schema JSONs exported to committed `base/schemas/` (required by `DbMigrationTest`).
- `state/*State` = Serializable+Cloneable UI wrappers (command inputs/outputs, notifier before/after snapshots).
- `FileHelper` (temp/export files, log file), `ImageHelper` (EXIF-aware).

## Adding a feature checklist (non-obvious)
- New entity ⇒ FULL stack: entity + `*Dao` (1:1, abstract @Dao) + repository + `*State` + `*ChangeNotifier` + `New/Update/Delete/Query/Paged*Cmd` + registration in the right ProviderModules (missing registration = runtime `lazyGet` failure); DB change ⇒ bump version + migration + commit schema JSON.
- New page ⇒ `Routes` constant + navMap entry in `AppProviderModule.getNavigator()` + dispose children in `dispose()`.
