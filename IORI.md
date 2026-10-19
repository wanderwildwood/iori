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
  what each is doing (on, off here, not installed, needs an update, nothing now or off in the
  app, off in Glance, did not answer); pressing one there switches it on or off on home only.
  When Glance itself is the trouble it says so instead: not installed, needs 0.1.24, does not
  know this build, or did not answer. With no lines to press, the list is still reached from a
  "Home lines" row that home shows while it is being edited, and from **Settings → Home
  lines**, beside About inkOS:wndr. The list turns its pages on whole rows, like the house apps'
  lists (`ui/PagedColumn.kt`, the canonical copy with only its package line changed), with no
  scrollbar, and Close stays under it at any text size. A source that is not installed is named
  as its launcher names it ("Medicine", "Wallet"), from a fixed list in `IoriNames.kt`, then by
  its installed label, then by its package.
- **If iori's own code ever crashes**, inkOS's crash report still comes up, and home then draws
  without its lines and says "Home lines turned off after a crash". A long press on that row
  turns them back on.
- **Contacts.** A contact found in search opens on its page in Contacts (enishi) when that is
  installed, and inkOS's own chooser otherwise.
- **Lock-screen music** for any player, and an Events widget that shows all-day events on
  their own date.
- **Two buttons in the Settings header** where inkOS has Buy Me a Coffee: a coffee cup that still
  opens gezimos's Buy Me a Coffee ("Buy the author of inkOS a coffee"), and a llama ("Feed the
  llamas") for inkOS:wndr's own. Line drawings at 2.0 on a 24 grid (`iori_support_coffee.xml`,
  `iori_support_llama.xml`), sized to the words they replace. gezimos's Support page under About
  is unchanged.
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
'!app/src/iori/**' --glob '!IORI.md'` must list exactly these, 20 in all.

| File:line | Purpose |
|---|---|
| `app/build.gradle.kts:185` | `apply(from = "iori.gradle")`: identity, iori sources and strings |
| `app/src/main/java/com/github/gezimos/inkos/data/Migration.kt:8` | migrations run on `UPSTREAM_VERSION_CODE`, so upstream's thresholds still fire; line 258 writes back the same value |
| `app/src/main/java/com/github/gezimos/inkos/data/Migration.kt:152` | migrated internal slots point at `BuildConfig.APPLICATION_ID`, not `app.inkos` |
| `app/src/main/java/com/github/gezimos/inkos/data/Prefs.kt:709` | imports pass through `IoriBackup.rewrite`, so an inkOS backup restores into iori |
| `app/src/main/java/com/github/gezimos/inkos/ui/RecentsFragment.kt:672` | iori is left out of its own Recents |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt:400` | `IoriLines(state, showEditMode)`, after the media widget, before the bottom widget |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt:3601` | the placeholder icon uses iori's own package |
| `app/src/main/java/com/github/gezimos/inkos/helper/IconUtility.kt:280` | `isSyntheticPackage` accepts iori's package, and `app.inkos` only when this build is inkOS, so the real inkOS beside it is an ordinary app |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt:467` | `Iori.guardCrashes(this)`, right after onCreate puts CrashHandler back |
| `app/src/main/java/com/github/gezimos/inkos/helper/SearchHelper.kt:585` | a contact opens in enishi first, inkOS's chooser if that fails |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt:389` | the Settings header's Buy Me a Coffee button becomes `IoriAbout.HeaderButtons`: a cup that opens gezimos's Buy Me a Coffee, and a llama that opens this app's donation page (upstream's 27 lines of the button are gone from here) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt:628` | iori's Settings rows under inkOS's own About: "Home lines" (the lines dialog) and "About inkOS:wndr" |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt:843` | Advanced → Restart's description, written in code, through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/OnboardingScreen.kt:351` | the first-run pages' body text, written in code, through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/helper/utils/BrightnessHelper.kt:17` | the brightness-gesture toast, written in code, through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/notifications/NotificationsUI.kt:400` | the notification-access message reads `perm_notification_body` (upstream's same words), which iori overrides |
| `app/src/main/java/com/github/gezimos/inkos/ui/notifications/LettersFragment.kt:305` | the same, in Letters |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeBottomSheet.kt:1080` | the gesture sheet's "inkOS" tab title through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeBottomSheet.kt:1618` | the shortcut sheet's "inkOS" tab title through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeBottomSheet.kt:2292` | "What is inkOS Wallpaper?" reads `what_is_inkos_wallpaper` (upstream's same words), which iori overrides |

Not hooks, but carried on top of v0.6:

- `upstream-fixes`: "Honour canShowBadge() so service notifications stop badging" and
  "Events widget: all-day events by their date". Each is its own commit so it can be offered
  to gezimos as an issue patch.
- `main`: "Music controls on the lock screen, for any player" and "Lock-screen music as the
  players' own strip, in their place", iori only.
- `signing/.gitignore`: keeps the release key in `signing/` out of the repository.

String overrides in `app/src/iori/res/values/iori_strings.xml` (build-type resources outrank
main's): `accessibility_service_name`, `accessibility_service_description` (iori's wording,
which also covers lock-screen music), `accessibility_permission_message`,
`quote_widget_label`, `notification_widget_label`, `shortcut_settings_short` and
`inkos_settings` (the icon's long-press shortcut and the quick menu), `perm_notification_body`,
`toast_private_space_default_home_screen`, the first-run and Settings words that mean this app
(`settings_exit_inkos_title`, `gesture_tab_inkos`, `no_inkos_shortcuts`, `text_biometric_login`,
`wallpaper_inkos`, `about_inkos_wallpaper`, `what_is_inkos_wallpaper`,
`inkos_wallpaper_explanation`, `privacy_no_data_body`, `privacy_no_server_body`,
`privacy_local_body`, `privacy_open_source_body`), and `acra_email` (crash reports from inkOS:wndr go to
email@wanderthe.dev, never to gezimos). `lockscreen_music_nothing` is iori's own and lives there
too, so main's `strings.xml` differs from upstream only by the `upstream-fixes` `events_*`
strings. If upstream rewords one of these, carry the new wording over by hand. Upstream's words that are
written in code, not in strings.xml, go through `IoriNames.named`, which turns "inkOS" as a word on
its own into "inkOS:wndr" and leaves links alone; it is used only where the words mean this app,
never on a credit to inkOS or gezimos. Only the English
default is overridden; another locale's upstream translation still says inkOS.

The real inkOS (`app.inkos`) installed beside iori stays out of iori's Recents and usage lists,
on purpose: upstream's own filter (RecentsFragment.kt:670-671, MainViewModel.kt:329) leaves it
out, a launcher is not something to go back to from Recents, and keeping upstream's lines as
they are keeps those files out of the carried diff. Everywhere else it is an ordinary app.

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
the tree, switched off; deleting them would conflict on every merge.

## Publishing the repository (first time)

Builds are local; GitHub must never run upstream's workflows. `build.yml` runs on a push to
`main`, and `release.yml` on any `v*` tag, and this clone holds upstream's `v0.4`, `v0.5` and
`v0.6`. So, in this order:

1. `gh repo create wanderwildwood/iori --public` (no push yet).
2. `gh api -X PUT repos/wanderwildwood/iori/actions/permissions -F enabled=false`: Actions off
   for the whole repository before anything reaches it.
3. `git push origin main` (and `upstream-fixes` by name). Never `--tags` or `--follow-tags`;
   iori's own tags are pushed one by one, by name.
4. `gh workflow disable build.yml` and `gh workflow disable release.yml`, now that GitHub knows
   them; the files stay in the tree.

## Testing the lines

Glance answers only builds it knows by package and certificate:

| Glance (hitome) | iori | Lines? |
|---|---|---|
| release | release (`com.wanderwildwood.iori`, iori's key) | yes |
| debug | release | yes |
| debug | debug (`com.wanderwildwood.iori.debug`, the build box's or the desk's debug key) | yes, with the fault switches |
| release | debug | no: "Glance does not know this build" |

The fault switches in the lines dialog (a source made to throw, hang or come back empty) are
in debug iori only, so they need debug Glance too. A debug Glance is signed with Glance's own
release key, like its releases, so it installs over a release and back.
