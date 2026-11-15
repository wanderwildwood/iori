# Home (iori, 庵)

Home is inkOS by gezimos with a few additions for the Mudita Kompakt. It installs beside
inkOS as `com.wanderwildwood.iori`, so both can be on one phone and either can be the home app.

Based on [inkOS](https://github.com/gezimos/inkos) by gezimos, GPL-3.0. The lock-screen music
strip comes from gezimos's Katapult, GPL-3.0. Home is GNU General Public License v3.0
only. Source: https://github.com/wanderwildwood/iori

## What it adds

- **Lines on home.** Up to three lines under the home apps, in the quote's font, size and
  alignment: the next event, the sky, and whatever else Glance shows on the lock screen. They
  come from Glance (hitome), and the Music Box line follows Music Box or Music Box K, whichever is playing, so **lines need Glance 0.1.24 or later**, and they follow each
  app's own lock-screen switch: an app whose lock-screen line is off has no line on home
  either. A press opens the app a line came from. A long press opens a list of the sources and
  what each is doing (on, off here, not installed, needs an update, nothing now or off in the
  app, off in Glance, did not answer); pressing one there switches it on or off on home only.
  When Glance itself is the trouble it says so instead: not installed, needs 0.1.24, does not
  know this build, or did not answer. With no lines to press, the list is still reached from a
  "Home lines" row that home shows while it is being edited, and from **Settings → Home
  lines**, beside About Home. The list turns its pages on whole rows, like the house apps'
  lists (`ui/PagedColumn.kt`, the canonical copy with only its package line changed), with no
  scrollbar, and Close stays under it at any text size. A source that is not installed is named
  as its launcher names it ("Medicine", "Wallet"), from a fixed list in `IoriNames.kt`, then by
  its installed label, then by its package.
- **If iori's own code ever crashes**, inkOS's crash report still comes up, and home then draws
  without its lines and says "Home lines turned off after a crash". A long press on that row
  turns them back on.
- **Dark mode for every app.** Settings → Dark mode turns Home dark (inkOS's own dark theme)
  and is the one switch the other apps follow: each asks Home's read-only provider
  (`IoriDarkProvider`, two numbers: dark or not, and when the switch was last pressed) when it
  comes to the front (`IoriDark.kt`; the apps' side is `/opt/projects/shared-dark`). Home's own
  dialogs are drawn through an inverting filter while Home is dark (`ui/DarkWindow.kt`).
- **Hand-offs.** With no app picked for the clock or the date, a press opens Time or Datebook;
  a contact opens in Connections. When that app is not on the phone, Home says so and offers
  to get it; "Use the phone's own" is remembered and inkOS's behaviour comes back
  (`IoriHandOff.kt`).
- **Contacts.** A contact found in search opens on its page in Contacts (enishi) when that is
  installed, and inkOS's own chooser otherwise.
- **Lock-screen music** for any player, and an Events widget that shows all-day events on
  their own date.
- **Two buttons in the Settings header** where inkOS has Buy Me a Coffee: a coffee cup that still
  opens gezimos's Buy Me a Coffee ("Buy the author of inkOS a coffee"), and a llama ("Feed the
  llamas") for Home's own. The cup is a line drawing at 2.0 on a 24 grid
  (`iori_support_coffee.xml`); the llama is the same solid one as in About (`iori_llama.xml`). Both
  are sized to the words they replace. gezimos's Support page under About
  is unchanged.
- **Backups.** A backup exported from inkOS restores into Home with its internal slots
  (app drawer, notifications and the rest) and hidden entries still working.

## Two launchers on one phone

Each launcher is its own app to Android. Accessibility (shown as "Home actions"),
notification access, widget binds, pinned shortcuts and private-space access are granted to
each one separately and do not carry over.

On KompaktOS, as on any Android 13 or later, those two switches can be greyed out at first:
Android blocks them for apps installed from a file. Open App info, tap ⋮, then **Allow
restricted settings**, and try again.

## Hook ledger

Upstream files change only at these lines, each marked `// iori:`. Everything else iori adds is
in `app/iori.gradle` and `app/src/iori/`. After every merge, `rg -n '// iori:' --glob
'!app/src/iori/**' --glob '!*.md'` must list exactly these, 25 in all.

| File:line | Purpose |
|---|---|
| `app/build.gradle.kts:185` | `apply(from = "iori.gradle")`: identity, iori sources and strings |
| `app/src/main/java/com/github/gezimos/inkos/data/Migration.kt:8` | migrations run on `UPSTREAM_VERSION_CODE`, so upstream's thresholds still fire; line 258 writes back the same value |
| `app/src/main/java/com/github/gezimos/inkos/data/Migration.kt:152` | migrated internal slots point at `BuildConfig.APPLICATION_ID`, not `app.inkos` |
| `app/src/main/java/com/github/gezimos/inkos/data/Prefs.kt:714` | imports pass through `IoriBackup.rewrite`, so an inkOS backup restores into iori |
| `app/src/main/java/com/github/gezimos/inkos/ui/RecentsFragment.kt:672` | iori is left out of its own Recents |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt:400` | `IoriLines(state, showEditMode)`, after the media widget, before the bottom widget |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt:3601` | the placeholder icon uses iori's own package |
| `app/src/main/java/com/github/gezimos/inkos/helper/IconUtility.kt:280` | `isSyntheticPackage` accepts iori's package, and `app.inkos` only when this build is inkOS, so the real inkOS beside it is an ordinary app |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt:467` | `Iori.guardCrashes(this)`, right after onCreate puts CrashHandler back |
| `app/src/main/java/com/github/gezimos/inkos/helper/SearchHelper.kt:585` | a contact opens in enishi first, inkOS's chooser if that fails |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt:389` | the Settings header's Buy Me a Coffee button becomes `IoriAbout.HeaderButtons`: a cup that opens gezimos's Buy Me a Coffee, and a llama that opens this app's donation page (upstream's 27 lines of the button are gone from here) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt:628` | iori's Settings rows under inkOS's own About, four lines (628 to 631), each marked: "Dark mode" (Home and every app), "Your apps", "Folders" and "About Home" (which also holds "Home lines") |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/GestureHelper.kt:270` | a press on the clock with no app picked goes to Time, or to the offer to get it (`IoriHandOff.clock`); inkOS's own alarm intent when that is declined |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/GestureHelper.kt:291` | the same for the date and Datebook (`IoriHandOff.date`) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt:846` | Advanced → Restart's description, written in code, through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/OnboardingScreen.kt:79` | the first run is `IoriFirstRun.Show`, Home's own four pages (Welcome with privacy, Setup, Ecosystem, Theme and home apps); upstream's `OnboardingScreen.Show` below it is untouched and not called |
| `app/src/main/java/com/github/gezimos/inkos/helper/utils/BrightnessHelper.kt:17` | the brightness-gesture toast, written in code, through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/notifications/NotificationsUI.kt:400` | the notification-access message reads `perm_notification_body` (upstream's same words), which iori overrides |
| `app/src/main/java/com/github/gezimos/inkos/ui/notifications/LettersFragment.kt:305` | the same, in Letters |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeBottomSheet.kt:1080` | the gesture sheet's "inkOS" tab title through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeBottomSheet.kt:1618` | the shortcut sheet's "inkOS" tab title through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeBottomSheet.kt:2292` | "What is inkOS Wallpaper?" reads `what_is_inkos_wallpaper` (upstream's same words), which iori overrides |

Not hooks, but carried on top of v0.6:

- **Folders in the app drawer** (`data/Folders.kt`, and changes inside `ui/AppsFragment.kt`,
  `ui/compose/AppsUI.kt`, `ui/AppsDrawerState.kt`, `ui/dialogs/ComposeDialogManager.kt`,
  `data/Prefs.kt`, `data/repository/AppsRepository.kt`): these are edits to upstream's drawer,
  not one-line hooks, and will need care at the next upstream merge.
- **Your apps**, listed under sections (Core, then Outdoors, On the road, Daily life, Music,
  Reading and writing, Games and oracles, Tools: `SECTIONS` in `tools/make-apps-list.py`, which
  stops on an app with no section) (`IoriApps.kt`, `IoriAppsDialog.kt`, `assets/iori_apps.json` from
  `tools/make-apps-list.py`): three permissions and a receiver in the manifest. The first run
  (`IoriFirstRun.kt`) has the same list as its third page, Ecosystem, a page of tick boxes
  (`IoriPickApps.kt`), ahead of Theme and home apps so that what is got there can be put on the
  home screen: the core apps that are missing come ticked, GET fetches the ticked ones one
  after the other (`IoriApps.installAll`),
  and an install question put away without an answer is given up when the page is back in
  front (`IoriApps.resumed`), so the ones behind it are not held up.

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
`privacy_local_body`, `privacy_open_source_body`), and `acra_email` (crash reports from Home go to
email@wanderthe.dev, never to gezimos). `lockscreen_music_nothing` is iori's own and lives there
too, so main's `strings.xml` differs from upstream only by the `upstream-fixes` `events_*`
strings. If upstream rewords one of these, carry the new wording over by hand. Upstream's words that are
written in code, not in strings.xml, go through `IoriNames.named`, which turns "inkOS" as a word on
its own into "Home" and leaves links alone; it is used only where the words mean this app,
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
  `versionCode` 1, `versionName` 0.1.0, and `app_name` "Home" ("Home Debug");
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

## Publishing (first time)

Not done yet: there is no `wanderwildwood/iori` on GitHub, though `origin` already points
there. Nothing has been pushed or tagged. What is left, in this order:

**Before anything reaches GitHub**

Builds are local; GitHub must never run upstream's workflows. This clone carries two, in
`.github/workflows/`:

- `build.yml` ("Build APK"): runs on a push or pull request to `main`, and by hand.
- `release.yml` ("Build and Release APK"): runs on any `v*` tag, and by hand. It would build
  with upstream's settings and publish a release of its own.

This clone also holds upstream's tags `v0.1` to `v0.6`, so a stray `--tags` would set
`release.yml` off six times.

1. `gh repo create wanderwildwood/iori --public --description "inkOS with lines from Glance on
   home and music on the lock screen, for the Mudita Kompakt"` (no `--source`, no push).
2. `gh api -X PUT repos/wanderwildwood/iori/actions/permissions -F enabled=false`: Actions off
   for the whole repository before anything reaches it. Check with
   `gh api repos/wanderwildwood/iori/actions/permissions` (`"enabled": false`).

**Pushing**

3. The personal-data scrub and `gitleaks detect` over the tree and the history, and
   `git log upstream/main..main` read once more for anything that should not go out.
4. `git push origin main`, then `git push origin upstream-fixes`. Never `--tags`,
   `--follow-tags` or `--all`; iori's own tags go one by one, by name.
5. `gh workflow disable build.yml -R wanderwildwood/iori` and the same for `release.yml`,
   now that GitHub knows them. The files stay in the tree, so merges stay clean.
6. Repository settings: Issues on, Wiki and Projects off, topics `mudita-kompakt`, `eink`,
   `launcher`, `inkos`. There is no Sponsor button: upstream's `.github/FUNDING.yml` is
   removed here, and the cup in Settings is where gezimos's Buy Me a Coffee lives.

**The release**

7. Build the release locally at the pushed `main`. Check the APK is signed with iori's
   release key (SHA-256 `fb06881da0a2c7b0…`, the one Glance's `PanelForHome` knows) and that
   `aapt2 dump badging` says `com.wanderwildwood.iori`, `versionName='0.1.0'`,
   `versionCode='1'`.
8. Assets, named as the other apps' releases are (hanauta v0.1.0: `hanauta.apk`,
   `hanauta.apk.sha256`, `hanauta-v0.1.0.apk`, `hanauta-v0.1.0.apk.sha256`):
   `iori.apk`, `iori.apk.sha256`, `iori-v0.1.0.apk`, `iori-v0.1.0.apk.sha256`. Each `.sha256`
   is `sha256sum` output naming its own file. The README's download link is
   `releases/latest/download/iori.apk`, so `iori.apk` must be there.
9. `git tag -a v0.1.0 -m "Home 0.1.0"` on that commit, `git push origin v0.1.0`
   (that one tag, by name), then `gh release create v0.1.0 --repo wanderwildwood/iori
   --title v0.1.0 --notes-file <notes>` with the four assets. Notes below.

**After**

10. wanderthe.dev: a card in `index.html` of `wanderwildwood.github.io`, like Hum's,
    with `shots/iori.png` (400x627, from `screenshots/1-home.png`) and Download, Source and
    Release notes links. Draft:

        <div class="app" id="iori">
          <img class="shot" src="shots/iori.png" width="400" height="627" loading="lazy"
               alt="Home's home: four apps, and today's events and texts under them">
          <h4>Home <button type="button" class="jp" aria-pressed="false" title="日本語で読む">/ 庵 iori</button></h4>
          <p class="en">gezimos's inkOS launcher, with lines from Glance under the home apps and
          music on the lock screen for any player. Installs beside inkOS.</p>
          <p class="ja" lang="ja" hidden>gezimosのinkOSランチャーに、ホームのアプリの下へGlanceの行を、ロック画面にどのプレーヤーの音楽も。inkOSと並べて入る。</p>
          <p class="links">
            <a href="https://github.com/wanderwildwood/iori/releases/latest/download/iori.apk">Download</a>
            <a href="https://github.com/wanderwildwood/iori">Source</a>
            <a href="https://github.com/wanderwildwood/iori/releases/latest">Release notes</a>
          </p>
        </div>

11. Profile README (`wanderwildwood/wanderwildwood`): nothing to add; it points to
    wanderthe.dev for the list.
12. Glance's README already says it hands its lines to Home; once the repository is up,
    link the name there to it.
13. Upstream fixes: the two commits on `upstream-fixes` can go to gezimos as issue patches,
    each on its own.

### Draft release notes, v0.1.0

> First release. inkOS by gezimos, as its own app beside inkOS, with a few additions for the
> Kompakt. Up to three lines under the home apps from Glance (0.1.24 or later): today's
> events, the weather, what is unread, following the switches chosen there; a long press shows
> each source and what it is doing. Music controls on the lock screen for any player, above
> the padlock. A contact found in search opens in Contacts. All-day events show on their own
> date, and service notifications no longer badge their apps. Everything inkOS does still
> works, and a backup from inkOS restores into it. No internet permission.

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
