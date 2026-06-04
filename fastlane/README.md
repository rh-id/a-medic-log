This directory contains Google Play Store metadata for the a-medic-log app.

## Structure

The metadata follows the [fastlane supply](https://docs.fastlane.tools/actions/supply/) directory structure:

```
fastlane/
└── metadata/
    └── android/
        ├── en-US/          (English - United States)
        ├── de-DE/          (German - Germany)
        ├── fr-FR/          (French - France)
        ├── it-IT/          (Italian - Italy)
        ├── id/             (Indonesian)
        ├── et/             (Estonian)
        ├── is-IS/          (Icelandic - Iceland)
        ├── nb-NO/          (Norwegian Bokmål)
        ├── nn-NO/          (Norwegian Nynorsk)
        └── rm/             (Romansh)
```

Each locale directory contains:
- `title.txt` - App title (max 30 characters)
- `short_description.txt` - Short description (max 80 characters)
- `full_description.txt` - Full description with HTML (max 4000 characters)
- `images/` - App icon, feature graphic, and phone screenshots
- `changelogs/` - Version-specific release notes

## Changelogs

Changelog files are named by `versionCode` (e.g., `23.txt` for versionCode 23). During the Gradle build, the matching changelog is copied to `app/build/changelog.txt` which is then used as the GitHub Release body.

## Adding a new release

1. Increment `versionCode` in `app/build.gradle`
2. Create a new changelog file: `fastlane/metadata/android/en-US/changelogs/{versionCode}.txt`
3. Optionally copy the changelog to other locale directories

## Adding a new locale

1. Create a new directory under `fastlane/metadata/android/{locale}/`
2. Add `title.txt`, `short_description.txt`, `full_description.txt`
3. Copy or create locale-specific images in `images/`
4. Copy changelog files from `en-US/changelogs/`

For more info see https://docs.fastlane.tools/actions/supply/