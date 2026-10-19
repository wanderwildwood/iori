# inkOS:wndr (iori, 庵)

inkOS:wndr is inkOS by gezimos with a few additions for the Mudita Kompakt. It installs beside
inkOS as `com.wanderwildwood.iori`, so both can be on one phone and either can be the home app.

Based on [inkOS](https://github.com/gezimos/inkos) by gezimos, GPL-3.0. The lock-screen music
strip comes from gezimos's Katapult, GPL-3.0. inkOS:wndr is GNU General Public License v3.0
only. Source: https://github.com/wanderwildwood/iori

## What it adds

- **Lines on home.** Up to three lines under the home apps, in the quote's font, size and
  alignment: the next event, the sky, and whatever else Glance shows on the lock screen. They
  come from Glance (hitome), so **lines need Glance 0.1.24 or later**, and they follow each
  app's own lock-screen switch: an app whose lock-screen line is off has no line on home
  either. A press opens the app a line came from. A long press opens a list of the sources and
  what each is doing (on, off here, not installed, needs an update, off in the app, off in
  Glance, did not answer); pressing one there switches it on or off on home only.
- **If iori's own code ever crashes**, inkOS's crash report still comes up, and home then draws
  without its lines and says "Home lines turned off after a crash". A long press on that row
  turns them back on.
- **Contacts.** A contact found in search opens on its page in Contacts (enishi) when that is
  installed, and inkOS's own chooser otherwise.
- **Lock-screen music** for any player, and an Events widget that shows all-day events on
  their own date.
- **Backups.** A backup exported from inkOS restores into inkOS:wndr with its internal slots
  (app drawer, notifications and the rest) and hidden entries still working.

## Two launchers on one phone

Each launcher is its own app to Android. Accessibility (shown as "inkOS:wndr actions"),
notification access, widget binds, pinned shortcuts and private-space access are granted to
each one separately and do not carry over.

On KompaktOS, as on any Android 13 or later, those two switches can be greyed out at first:
Android blocks them for apps installed from a file. Open App info, tap ⋮, then **Allow
restricted settings**, and try again.

## Hook ledger

Upstream files change only at these lines, each marked `// iori:`. Everything else iori adds is
in `app/iori.gradle` and `app/src/iori/`. After every merge, `rg -n '// iori:' --glob
'!app/src/iori/**' --glob '!IORI.md'` must list exactly these, 11 in all.

| File:line | Purpose |
|---|---|
| `app/build.gradle.kts:185` | `apply(from = "iori.gradle")`: identity, iori sources and strings |
| `app/src/main/java/com/github/gezimos/inkos/data/Migration.kt:8` | migrations run on `UPSTREAM_VERSION_CODE`, so upstream's thresholds still fire; line 258 writes back the same value |
| `app/src/main/java/com/github/gezimos/inkos/data/Migration.kt:152` | migrated internal slots point at `BuildConfig.APPLICATION_ID`, not `app.inkos` |
| `app/src/main/java/com/github/gezimos/inkos/data/Prefs.kt:709` | imports pass through `IoriBackup.rewrite`, so an inkOS backup restores into iori |
| `app/src/main/java/com/github/gezimos/inkos/ui/RecentsFragment.kt:672` | iori is left out of its own Recents |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt:400` | `IoriLines(state, showEditMode)`, after the media widget, before the bottom widget |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt:3601` | the placeholder icon uses iori's own package |
| `app/src/main/java/com/github/gezimos/inkos/helper/IconUtility.kt:281` | `isSyntheticPackage` also accepts iori's package |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt:467` | `Iori.guardCrashes(this)`, right after onCreate puts CrashHandler back |
| `app/src/main/java/com/github/gezimos/inkos/helper/SearchHelper.kt:585` | a contact opens in enishi first, inkOS's chooser if that fails |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt:655` | one "About inkOS:wndr" row under inkOS's own About |

Not hooks, but carried on top of v0.6:

- `upstream-fixes`: "Honour canShowBadge() so service notifications stop badging" and
  "Events widget: all-day events by their date". Each is its own commit so it can be offered
  to gezimos as an issue patch.
- `main`: "Music controls on the lock screen, for any player" and "Lock-screen music as the
  players' own strip, in their place", iori only.
- `signing/.gitignore`: keeps the release key in `signing/` out of the repository.

String overrides in `app/src/iori/res/values/iori_strings.xml` (build-type resources outrank
main's): `accessibility_service_name`, `accessibility_permission_message`,
`quote_widget_label`, `notification_widget_label`. If upstream rewords one of these, carry the
new wording over by hand.

## Identity

`app/iori.gradle`, applied last from `app/build.gradle.kts`:

- reads upstream's `defaultConfig.versionCode` first and keeps it as
  `BuildConfig.UPSTREAM_VERSION_CODE`, so the field always equals the base it was built on;
- sets `applicationId` `com.wanderwildwood.iori` (debug keeps upstream's `.debug` suffix),
  `versionCode` 1, `versionName` 0.1.0, and `app_name` "inkOS:wndr" ("inkOS:wndr Debug");
- adds `src/iori/java` to main, `src/iori/res` to the debug and release resources, and
  `src/iori/test/java` to the unit tests;
- keeps iori's classes unrenamed in release (`src/iori/proguard-iori.pro`), because the crash
  guard knows its own code by class name.

`namespace` stays `com.github.gezimos.inkos`.

Signing: upstream's signing block is untouched. It reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` from the environment, and the build script fills them from
the gitignored `signing/`. Without them the release builds unsigned. There is no fallback key.

## Merging upstream

1. `git fetch upstream --tags`
2. `git checkout upstream-fixes && git merge vX.Y` (rerere is on: `git config rerere.enabled true`)
3. `git checkout main && git merge upstream-fixes`
4. Re-place any hook the merge moved, then check the ledger: the `rg` above must match the
   table, and `git diff --stat vX.Y..main -- ':!app/src/iori' ':!app/iori.gradle' ':!IORI.md'`
   must list only the ledger's files and the carried commits' files.
5. Check that `Iori.guardCrashes` still follows the line where MainActivity puts CrashHandler
   back, and that `UPSTREAM_VERSION_CODE` in the built BuildConfig equals the new base's code.
6. Update the line numbers in the table.

Never rebase a branch that has been pushed. The inherited `release.yml` and `build.yml` stay in
the tree, switched off with `gh workflow disable`; deleting them would conflict on every merge.
