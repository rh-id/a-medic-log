# Conventions (hand-enforced — NO formatter/linter exists)

## Java style
- Fields `m`-prefixed camelCase, private/protected (`mExecutorService`) — EXCEPTION: Room entities use public unprefixed fields. Constants SCREAMING_SNAKE. Classes public.
- Suffix taxonomy: `*Page` (full-screen StatefulView), `*SV` (child StatefulView widget: `AppBarSV`, `NoteItemSV`), `*Cmd`, `*ChangeNotifier`, `*State`, `*Repository`, `*Dao`, `*Module`, `*Section` (note-detail sub-components), `*Adapter`.
- Imports explicit, alphabetical groups, NO wildcards.
- StatefulView pages implement Serializable ⇒ provider-fetched fields are `private transient`, initialized in `onProvideComponent(Provider)` (re-fetched on restore); non-transient breaks page serialization. Comments sparse: javadoc only on non-obvious helpers; ~zero inline comments; no TODO/FIXME in main code.

## Rx rules (strict)
- EVERY subscription registered via `RxDisposer.add("tag", disposable)` (string tags) — disposal is automatic when the owning provider scope dies; unregistered = leak.
- Background work: commands `subscribeOn(Schedulers.from(mExecutorService.get()))` (WeightedThreadPool); UI observes `AndroidSchedulers.mainThread()`. Subjects must be `.toSerialized()`.
- `RxUtils.executeAndLog(...)` is the standard run+log helper.

## Resources / i18n
- Single `values/strings.xml` per locale; ALL 10 locales kept in EXACT parity (219 translatable strings + 5 plurals each; every locale file additionally carries `ok` as `translatable="false"` → 220 `<string>` entries per locale; the default `values/` also carries default-only `adherence_percent` as `translatable="false"` → 221 entries total). Supported languages: `values` (English), `values-de` (German), `values-et` (Estonian), `values-fr` (French), `values-in` (Indonesian — legacy Android code `in`, fastlane dir uses `id`), `values-is` (Icelandic), `values-it` (Italian), `values-nb` (Norwegian Bokmål), `values-nn` (Norwegian Nynorsk), `values-rm` (Romansh). `values-night` + `values-v35` for themes.
- New user-facing string ⇒ add to all 10 files at once. Missing key in ANY locale = lint MissingTranslation ERROR = `lint`/`build` fail (parity is build-enforced).
- String keys: snake_case role prefixes `title_/error_/success_/form_/hint_/confirm_/menu_/toast_`; plurals `*_count_*`.
- Layouts: `page_/item_/list_/dialog_/menu_` prefixes; vector icons `ic_<name>_black/_white` in `drawable-anydpi`; vector pathData decimals need LEADING ZEROS (`0.5`, never `.5`) — AGP lint InvalidVectorPath fails the build otherwise.

## Gotchas (learned the hard way)
- Room `@Insert` does NOT write back the autoincrement id into the entity's public field — capture the returned id (`x.id = dao.insert(x)`) whenever a later assertion/navigation uses it (test failures otherwise: "expected 0").
- aapt2 strips UNescaped double quotes in string resources — always escape as `\"` (a `"%s"` otherwise renders without quotes while translations that escape them render with them).
- ALL confirm dialogs go through `UiUtils.showConfirmDialog` → a-navigator `ConfirmDialog` extension (red CANCEL / blue OK, single shared style app-wide; ~3 of ~10 call sites are non-destructive confirms). NEVER add `AlertDialog.Builder`/`MaterialAlertDialogBuilder` in app code.
- List-row action buttons: constrain the action container `Bottom_toBottomOf=parent` + `Top_toBottomOf=<last content view>` (bottom-right, per `item_note`/`item_medicine`); do NOT center with top+bottom parent constraints. GONE out empty optional lines — empty TextViews still measure one line.
- Instrumented-test idioms: base DAO/repository tests = Room test DB + `MigrationTestHelper` + `LinkedHashSetConverter` + per-test seeding helpers; app command tests use `ExportImportTestProviderModule` + real DAOs.
- Windows EOL churn: repo relies on `core.autocrlf=true` (CRLF worktree, LF repo; `.gitattributes` only covers licenses.html). Agent tools that rewrite whole files (sed -i sweeps, bulk writes) serialize with LF → mass `git status` noise where files differ by EOL only (content unchanged; verified via `git diff --ignore-cr-at-eol`). Fix: `git add --renormalize .` — EOL-only entries drop out, commit content unaffected.

## Git
- Trunk-based `master`; releases via `v*` tags.
- Commit style: conventional, lowercase, no scopes: `feat:/fix:/refactor:/docs:/chore:/ci:/build:/release:/dep:`. Old `[Bracket]` style (e.g. `[Fastlane]`) is superseded — do not reuse. Version bumps pair build.gradle version + all-locale changelogs in one commit.
