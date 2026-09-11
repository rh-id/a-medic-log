# Conventions (hand-enforced — NO formatter/linter exists)

## Java style
- Fields `m`-prefixed camelCase, private/protected (`mExecutorService`) — EXCEPTION: Room entities use public unprefixed fields. Constants SCREAMING_SNAKE. Classes public.
- Suffix taxonomy: `*Page` (full-screen StatefulView), `*SV` (child StatefulView widget: `AppBarSV`, `NoteItemSV`), `*Cmd`, `*ChangeNotifier`, `*State`, `*Repository`, `*Dao`, `*Module`, `*Section` (note-detail sub-components), `*Adapter`.
- Imports explicit, alphabetical groups, NO wildcards. Comments sparse: javadoc only on non-obvious helpers; ~zero inline comments; no TODO/FIXME in main code.

## Rx rules (strict)
- EVERY subscription registered via `RxDisposer.add("tag", disposable)` (string tags) — disposal is automatic when the owning provider scope dies; unregistered = leak.
- Background work: commands `subscribeOn(Schedulers.from(mExecutorService.get()))` (WeightedThreadPool); UI observes `AndroidSchedulers.mainThread()`. Subjects must be `.toSerialized()`.
- `RxUtils.executeAndLog(...)` is the standard run+log helper.

## Resources / i18n
- Single `values/strings.xml` per locale; ALL 10 locales kept in EXACT parity (139 strings + 4 plurals each). Supported languages: `values` (English), `values-de` (German), `values-et` (Estonian), `values-fr` (French), `values-in` (Indonesian — legacy Android code `in`, fastlane dir uses `id`), `values-is` (Icelandic), `values-it` (Italian), `values-nb` (Norwegian Bokmål), `values-nn` (Norwegian Nynorsk), `values-rm` (Romansh). `values-night` + `values-v35` for themes.
- New user-facing string ⇒ add to all 10 files at once.
- String keys: snake_case role prefixes `title_/error_/success_/form_/hint_/confirm_/menu_/toast_`; plurals `*_count_*`.
- Layouts: `page_/item_/list_/dialog_/menu_` prefixes; vector icons `ic_<name>_black/_white` in `drawable-anydpi`; vector pathData decimals need LEADING ZEROS (`0.5`, never `.5`) — AGP lint InvalidVectorPath fails the build otherwise.

## Git
- Trunk-based `master`; releases via `v*` tags.
- Commit style: conventional, lowercase, no scopes: `feat:/fix:/refactor:/docs:/chore:/ci:/build:/release:/dep:`. Old `[Bracket]` style (e.g. `[Fastlane]`) is superseded — do not reuse. Version bumps pair build.gradle version + all-locale changelogs in one commit.
