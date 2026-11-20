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
  screen → Home lines**. The list turns its pages on whole rows, like the house apps'
  lists (`ui/PagedColumn.kt`, the canonical copy with only its package line changed), with no
  scrollbar, and Close stays under it at any text size. A source that is not installed is named
  as its launcher names it ("Medicine", "Wallet"), from a fixed list in `IoriNames.kt`, then by
  its installed label, then by its package.
- **If iori's own code ever crashes**, inkOS's crash report still comes up, and home then draws
  without its lines and says "Home lines turned off after a crash". A long press on that row
  turns them back on. A crash from iori's code during a first run also moves the first run past
  the page that was up (`IoriFirstRun.tripped`; on the last page it ends the first run), so one
  broken page cannot bring the launcher back to itself for ever; Run setup again gives it
  another go.
- **Dark mode for every app.** Settings → Dark mode turns Home dark (inkOS's own dark theme)
  and is the one switch the other apps follow: each asks Home's read-only provider
  (`IoriDarkProvider`, two numbers: dark or not, and when the switch was last pressed) when it
  comes to the front (`IoriDark.kt`; the apps' side is `/opt/projects/shared-dark`). Home's own
  dialogs are drawn through an inverting filter while Home is dark (`ui/DarkWindow.kt`).
  When the setting changes Home closes its own dialogs before it recreates the screen
  (`ui/HouseDialogs.kt`): recreated under an open one (Your apps with a folder open), the
  dialog went but accessibility kept its window, and TalkBack read nothing from Home.
  The switch is only on MuditaOS, which has no dark setting a person can reach (it keeps
  Android's dark-theme screen installed, so asking Settings for it cannot tell). MuditaOS is
  told by Mudita's Settings app (`com.mudita.settings`) on a system that is not LineageOS
  (`Build.DISPLAY` or `ro.lineage.*`), which keeps KompaktOS apart; `IoriDark.systemHasDarkSetting`
  is the one place this is decided. Anywhere else from Android 10 on, the system's setting is
  the switch: Home's row opens the system's dark-theme screen (or display settings), the first
  run's light/dark button is not drawn, Home's theme follows the system's night setting, and
  the provider answers "not dark" whatever the switch was left at, so a switch left on under
  MuditaOS cannot hold the apps dark after a move to KompaktOS.
- **One Nextcloud login.** Home signs in to the person's Nextcloud once (first run, page 5, or
  Settings → Nextcloud account): Login Flow v2 in the browser, or a password typed in where
  there is no browser: a login password typed there is used once to get an app password from
  the server (`core/getapppassword`) and is never kept; an app password typed there is kept as
  it is. It keeps ONE app password (Nextcloud will not make an app password
  from an app password), sealed by a keystore key, in a file left out of backups
  (`IoriAccount.kt`). `IoriAccountProvider` (`content://<package>.accounts/nextcloud`: server,
  user, password, stamp, uid) hands it only to callers whose package AND signing certificate are
  pinned in `IoriAccount.CALLERS` (every app of Your apps that signs in to a Nextcloud, eleven; the test holds the list to
  `iori_apps.json`), and a change notice goes out on sign-in and sign-out. `user` is the login
  name (basic auth); `uid` is the Nextcloud user id (`ocs.data.id` of `cloud/user`, asked once
  after sign-in, and filled in later for an account kept without one; until then it reads as
  the login name), which every app builds its DAV paths with. The server is kept written one
  way (lower-case scheme and host, no default port), as the apps' `sameServer` writes it. Login
  Flow v2's sign-in and poll addresses must be on the typed host, and the server it names is
  kept only when it is. Sign out stops serving the account at once and asks the server to
  delete the app password; until the server says it is gone (200, 401 or 404) the password
  stays sealed under `revoke`, is tried again at each start of Home (the provider's
  `onCreate`) and each time the account is shown, and the rows say so. A sign-in with that
  same app password on the same server takes it off the list (it is live again, never ended). https only; a debug build also takes plain http to a
  host written as a number on its own network or the phone itself (127/8, 10/8, 172.16/12,
  192.168/16, ::1), or localhost; a name is never looked up, for a stand-in server (`app/src/debug/AndroidManifest.xml`). The
  apps' side is `sync/HomeAccount.kt`, the same file in each app that has the row "Use the
  phone's Nextcloud account".
- **Settings in Home's own order** (`IoriSettings.kt`, makeover piece 2, 11-19): the top page is
  Home's, drawn with inkOS's rows and headings: **Phone-wide** (Dark mode, Nextcloud account,
  Default apps; font and size come here later), **Apps** (Your apps, the one row and the one
  page for the house's apps, `IoriYourApps`; see below), **Home** (Home screen, Look & feel, Gestures, Advanced),
  and **Setup and about** (Run setup again, which opens the first run at its first page and
  resets nothing; the two ticks come ticked, and BACK on the first page goes back to Settings; About Home, with the credit to gezimos and inkOS's own About as a row of it).
  Home screen is a sub-page holding inkOS's own home-page rows as they are (Home, App drawer,
  Fonts, Notifications, private space and work profile when there are any, Extras), then Folders
  and Home lines. Look & feel, Gestures and Advanced are inkOS's pages, opened from the top page;
  none of them is rewritten.
- **Hand-offs.** With no app picked for the clock or the date, a press opens Time or Datebook;
  a contact opens in Connections. When that app is not on the phone, Home says so and offers
  to get it; "Use the phone's own" is remembered and inkOS's behaviour comes back
  (`IoriHandOff.kt`).
- **Contacts.** A contact found in search opens on its page in Contacts (enishi) when that is
  installed, and inkOS's own chooser otherwise.
- **Lock-screen music** for any player, and an Events widget that shows all-day events on
  their own date.
- **Two rows in About Home** for what inkOS has as a Buy Me a Coffee button in the Settings
  header (since the 11-19 house pass the header has no buttons): a coffee cup that still opens
  gezimos's Buy Me a Coffee ("Buy the author of inkOS a coffee"), and a llama ("Feed the
  llamas") for Home's own. The cup is a line drawing at 2.0 on a 24 grid
  (`iori_support_coffee.xml`); the llama is `iori_llama.xml`. gezimos's Support page under
  inkOS's About is unchanged.
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

Upstream base: `v0.6` (inkOS 0.6, versionCode 101013).

Upstream files change only at the lines below, each marked with `iori:` in a comment. Everything
else iori adds is in its own files: `app/iori.gradle`, `app/src/iori/`, `app/src/debug/`
(debug's manifest overlay), and the new files of the carried commits. A run of changed lines is
held between `iori: begin` and `iori: end` (written `6-15` below; both ends are markers, and every
line between them counts as marked). The tree has **210** marker lines.

What Home adds to the merged manifest is the two `AndroidManifest.xml` blocks below (four
permissions; two exported providers and a receiver) and, in debug builds only,
`usesCleartextTraffic` from `app/src/debug/AndroidManifest.xml` (checked in the build's
manifest-merger report, 19 Nov: every other permission, provider and receiver is upstream's
or a library's).

`tools/iori-hooks.py` checks all of it and exits 1 on any difference: every marker at the line
the table gives, the total, the rows in file and line order, and that every line added to an
upstream file since the base is marked, inside a block, or in a carried file below with the
count of unmarked lines the table gives. So a hook half lost in a merge shows even when the
number of markers is still right. `tools/iori-hooks.py --list` prints every marker; use it to
write the new line numbers into the table after a merge.

| File | Lines | Purpose |
|---|---|---|
| `app/build.gradle.kts` | 8 | the AboutLibraries plugin applied: it writes `res/raw/aboutlibraries.json`, the list behind About Home's Licenses drawer; set up in `app/iori.gradle` |
| `app/build.gradle.kts` | 186 | `apply(from = "iori.gradle")`: identity, iori sources and strings |
| `app/src/main/AndroidManifest.xml` | 6-15 | four permissions: `INTERNET` and `REQUEST_INSTALL_PACKAGES` (Your apps fetches an app and hands it to Android; the Nextcloud sign-in), `SET_ALARM` (Default apps asks Android which clock app), `UPDATE_PACKAGES_WITHOUT_USER_ACTION` (Android 12+ updates an app Home installed with no question) |
| `app/src/main/AndroidManifest.xml` | 173-191 | at the end of `<application>`: `IoriDarkProvider` (`${applicationId}.dark`, exported, read-only: the other apps' dark switch), `IoriAccountProvider` (`${applicationId}.accounts`, exported; it checks each caller's package and certificate itself: the phone's one Nextcloud account), `IoriInstallReceiver` (not exported: the installer's answers). If a merge drops this block the build still passes and the apps quietly lose dark mode and the account; the hook check above is what catches it |
| `app/src/main/java/com/github/gezimos/common/ContextExtensions.kt` | 200 | before Accessibility opens: where Android 13+ blocks an app installed from a file, a toast says how to allow restricted settings (`IoriKompaktOS.restrictedBlocked`) |
| `app/src/main/java/com/github/gezimos/common/CrashHandler.kt` | 30, 33, 34, 157 | 19 Nov audit: `logUserAction` keeps nothing, and the crash report has no "Recent User Actions" section. inkOS's callers log each app and shortcut opened from home (package, shortcut id, time), which went into the report emailed to the developer; the callers are left as they are |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt` | 191 | while the first run is up `onKeyDown` swallows every key, so a menu, Esc or Home key cannot leave it; Back is let through, else the first run's own `BackHandler` never ran (a page back; on page 1 of Run setup again, back to Settings) |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt` | 431, 433 | KompaktOS (`IoriKompaktOS`): once per install the real status bar on and the swipe down left to the system shade; inkOS's meink helper not started there, as its Kompakt app owns the e-ink modes |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt` | 468 | `Iori.guardCrashes(this)`, right after onCreate puts CrashHandler back |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt` | 469 | `IoriDark.atStart(...)`, before onCreate reads the theme: where the system has a dark setting of its own, Home's theme is the system's |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt` | 518 | the first run is opened only when it is not already in the restored back stack (`IoriFirstRun.notShown`): a font, size or language change, or the process ended while away in Android's settings, put a second copy on top, and FINISH then landed on page 1 of the one underneath |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt` | 519 | on a first run, inkOS's e-ink Light theme is not set where the system has a dark setting (`IoriDark.systemHasDarkSetting`): Home took the system's at line 468 |
| `app/src/main/java/com/github/gezimos/inkos/MainActivity.kt` | 645 | `IoriDark.systemChanged(...)` in `onConfigurationChanged`: the system's dark setting changed while Home was up, and Home turns with it |
| `app/src/main/java/com/github/gezimos/inkos/data/Constants.kt` | 377, 408, 424, 452 | 19 Nov: no Hoog font. The `FontFamily.Hoog` entry and its three `when` branches are each replaced by a marker comment; a stored `Hoog` is made Public Sans at each start by `IoriRemoved.migrate`, called from `Iori.guardCrashes` |
| `app/src/main/java/com/github/gezimos/inkos/data/Migration.kt` | 8 | migrations run on `UPSTREAM_VERSION_CODE`, so upstream's thresholds still fire; the write-back at the end of the same function stores that value |
| `app/src/main/java/com/github/gezimos/inkos/data/Migration.kt` | 152 | migrated internal slots point at `BuildConfig.APPLICATION_ID`, not `app.inkos` |
| `app/src/main/java/com/github/gezimos/inkos/data/Prefs.kt` | 371 | a theme import leaves Theme mode (`APP_THEME`) alone where the system has a dark setting, and leaves out a theme's `INKOS_WALLPAPER_RESOURCE_ID` when it is a picture (`IoriRemoved.removedWallpaper`), which in this build would name another resource |
| `app/src/main/java/com/github/gezimos/inkos/data/Prefs.kt` | 714 | imports pass through `IoriBackup.rewrite`, so an inkOS backup restores into Home |
| `app/src/main/java/com/github/gezimos/inkos/data/Prefs.kt` | 718 | a full backup restore leaves Theme mode alone where the system has a dark setting |
| `app/src/main/java/com/github/gezimos/inkos/data/Prefs.kt` | 719 | on KompaktOS a full backup restore keeps the status bar on and does not bring back a swipe down to inkOS's tray (`IoriKompaktOS.keepOnRestore`): a MuditaOS backup would undo the KompaktOS defaults on the move |
| `app/src/main/java/com/github/gezimos/inkos/data/ThemePresets.kt` | 184, 185, 207, 208, 346, 347, 369, 370 | 19 Nov: the four themes that set a picture (SF, Leaf, NYC, Forrest) set none (`wallpaperResourceId = null`) and a solid background (`backgroundOpacity = 255`; inkOS's 175, 160, 10, 160 in the comments) |
| `app/src/main/java/com/github/gezimos/inkos/helper/EditModeHelper.kt` | 992 | Edit mode → Background → Theme Mode is hidden where the system has a dark setting |
| `app/src/main/java/com/github/gezimos/inkos/helper/IconUtility.kt` | 280 | `isSyntheticPackage` accepts Home's package, and `app.inkos` only when this build is inkOS, so the real inkOS beside it is an ordinary app |
| `app/src/main/java/com/github/gezimos/inkos/helper/SearchHelper.kt` | 585 | a contact opens in Contacts (enishi) first, inkOS's chooser if that fails |
| `app/src/main/java/com/github/gezimos/inkos/helper/WallpaperUtility.kt` | 27 | 19 Nov: the fourteen picture wallpapers are gone from `PRESET_WALLPAPERS`; one marker comment stands for the fourteen lines. White, Black and the generated patterns stay |
| `app/src/main/java/com/github/gezimos/inkos/helper/utils/BrightnessHelper.kt` | 17 | the brightness-gesture toast, written in code, through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/helper/utils/EinkRefreshHelper.kt` | 50 | the e-ink refresh gesture asks KompaktOS for its own full refresh (broadcast `com.kompakt.service.action.REFRESH`); elsewhere inkOS's overlay flash as before |
| `app/src/main/java/com/github/gezimos/inkos/style/SettingsTheme.kt` | 94 | the house side margin on Settings pages (`IoriSettingsLook.ROW_SIDE`) |
| `app/src/main/java/com/github/gezimos/inkos/ui/RecentsFragment.kt` | 671, 672 | Home is left out of its own Recents (671 only gains the `||` for 672) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 156, 169, 464 | the Settings restructure: a route `IoriHomeScreen`, its title, and its page, which is inkOS's own `SettingsHomeContent` |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 167, 168 | the About and Extras page titles read from strings (`iori_upstream_about_title`, `extra_features`) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 187, 269, 277 | Back returns to the page a sub-page was opened from (`IoriSettingsPages.PageMemory`) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 376, 394 | Back in the top bar, and no buttons there: the coffee cup and the llama are rows in About Home (upstream's Buy Me a Coffee button lines are gone from here) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 403, 424, 425, 426 | whole-row pages through `IoriSettingsPages.Pages` in place of inkOS's pager and page dots. Upstream's ~60-line pager is deleted here, not bypassed, so an upstream change to it conflicts |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 481, 505, 513, 556 | on the Home route, `SettingsHomeContent` returns `IoriSettings.Top(...)` at its first line (481: Phone-wide, Apps, Home, Setup and about); on the Home screen sub-page `if (false)` hides Look & feel, Gestures and Advanced, which are rows of the top page |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 572 | `if (false)` before inkOS's own About row; its page (gezimos's credits and links, untouched) opens from a row of About Home |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 580 | `IoriSettings.Foot(...)`: Folders and Home lines close the Home screen sub-page |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 669 | the e-ink mode row is not shown on KompaktOS (its Kompakt app owns the modes) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 679, 903, 917, 937, 965, 975, 987, 993, 1147, 1346, 1359, 1657, 1677, 2084 | rows that cycled through their choices in place open a house list (`iori.einkMode`, `iori.choose`): e-ink mode, the alignments, clock format, clock style, date format, notification count, sort order, icon shape, button corners, app name type; each writes what the cycle wrote |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 772, 773 | not being the default launcher is said in words (`iori_not_default`), not with a star |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 777 | `if (false)` before inkOS's Onboarding row: Run setup again on the top page does it |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 791, 797, 803, 1502, 1939, 1942 | inkOS's words written in code, read from iori strings: the notes on Guide, Restart (this app's name), Exit and UI scale, and the gesture-vs-page row's label and note |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 838, 846, 854, 862, 1334, 2067, 2075, 2078, 2089, 2096, 2103, 2113, 2126, 2136 | text sizes and gaps carry their unit ("sp", "dp"; `IoriSettingsLook.sp`) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 986 | a note that only repeated its label is left out |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 1200 | the widget height with a spaced "dp" |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 1504 | UI scale opens `iori.uiScale`, a house list, in place of inkOS's own sheet |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 1575 | `if (false)` before inkOS's Theme mode row in Look & feel: the one dark switch is Dark mode at the top of Settings |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 1868, 1878, 1890, 1902, 1927, 1946, 1958 | no gesture-number marks on the Gestures rows |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 1940 | no "Read more" on the gesture-vs-page row; the row opens the explanation |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 2010, 2027, 2117, 2382 | rows and headings that repeated each other are named apart (Fonts › One font everywhere; Letters window › Window title, Show the window) |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 2014 | "Reset all" at the house floor size |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 2121, 2151, 2152 | fonts by the font list's own name, on their own line, not cut at 12 |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 2303 | before notification access opens, while it is off: the restricted-settings toast, as in ContextExtensions |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 2355, 2373, 2389, 2404 | allowlists read "All apps" or "12 apps" (inkOS lets every app through while a list is empty), the character limit "20 characters" |
| `app/src/main/java/com/github/gezimos/inkos/ui/SettingsFragment.kt` | 2387, 2388, 2390 | Clear when app opens and the Letters allowlist are hidden while the Letters window is off |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/AppsUI.kt` | 36, 1427, 1461 | 11-19 MuditaOS 2.0: the app drawer's long-press menu gains "E-ink mode" above App info, only where `IoriEinkModes` can read the phone's per-app modes; a pick writes the same table Mudita's own E-ink Modes screen uses |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/GestureHelper.kt` | 270 | a press on the clock with no app picked goes to Time, or to the offer to get it (`IoriHandOff.clock`) |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/GestureHelper.kt` | 291 | the same for the date and Datebook (`IoriHandOff.date`) |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 334 | `IoriLinesAtTop(...)`: the home lines under the date line, when Home lines > Place on home is Top |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 340, 342 | `IoriLinesPlace.appsRoom()` and `appsAlignment()` on the home apps' box: with the lines at the top the apps' last row sits at the bottom |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 386, 387 | `IoriLinesPlace.footMeasured` on the foot column, so the apps know how much room it takes (386 only loses its comma) |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 403 | `IoriLinesPlace.PlayerRoom(...)`: with the lines at the top and the media widget on, the player's room stays at the foot while nothing plays |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 405 | `IoriLines(state, showEditMode)`, after the media widget, before the bottom widget |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 3146 | the home player has no outline round it |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 3162 | the player's icon tile is a square with 8 dp round corners, the Kompakt app icon's shape |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 3183-3190 | the player's highlight follows the tile's shape (`drawRoundRect` in place of `drawCircle`) |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 3604 | the placeholder icon uses Home's own package |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 4698 | `if (false)` before the pinch menu's About: About Home is in Settings |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/OnboardingScreen.kt` | 79 | the first run is `IoriFirstRun.Show`, Home's own pages; upstream's `OnboardingScreen.Show` is untouched and not called (see Shadowed upstream code) |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 55, 56 | imports of `IoriSettingsLook` and `settingsRow` |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 207, 428, 497, 575 | the house row spacing |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 219, 294, 439, 508, 586 | each row and heading reports its place (`settingsRow`) for the whole-row pages |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 235, 248 | no icon on a row |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 263, 267, 462, 466, 532, 546, 609, 613 | house text sizes, following inkOS's Settings text size |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 266, 465, 545, 612 | an emptied note takes no line |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 299-308 | a plain bold heading: no bullet, no capitals, no dashed line |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 331, 333, 335 | the page title: no capitals, house size, bold face |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 431, 500, 578 | a row that cannot be used is left out, not drawn in grey |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 475-482 | the house switch in place of `CustomToggleSwitch`; the whole row is still the press |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 535-544 | a row's value goes under its label |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 536 | (inside the block above) alignment values in words too |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/SettingsComposable.kt` | 556, 561 | the alignment glyph is off (`ALIGN_GLYPH`) and the value is no longer drawn on the right |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/ThemePresetsUI.kt` | 666, 705, 768 | Settings → Themes: the light/dark/system button in the header is not drawn, nor reached by the D-pad, where the system has a dark setting |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeBottomSheet.kt` | 1080 | the gesture sheet's "inkOS" tab title through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeBottomSheet.kt` | 1618 | the shortcut sheet's "inkOS" tab title through `IoriNames.named` |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeBottomSheet.kt` | 2292 | "What is inkOS Wallpaper?" reads `what_is_inkos_wallpaper` (upstream's same words), which iori overrides |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | 23 | `val iori = IoriDialogs(...)`: Home's house dialogs beside inkOS's sheet host; the manager's after-close step runs once, whichever closes |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | 109 | `showSliderDialog` → `iori.number`, the house number dialog; inkOS's sheet below is not reached (see Shadowed upstream code) |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | 140 | `showSingleChoiceDialog` → `iori.choose`, a house list; inkOS's sheet only for a caller that deletes rows (none today) |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | 175 | `showTabbedFontPicker` → `iori.fonts`: one list of fonts in their own faces |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | 223 | `showGestureActionPicker` → `iori.gesture`: one list in place of three tabs |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | 425 | `dismissAll` also closes a house dialog |
| `app/src/main/java/com/github/gezimos/inkos/ui/notifications/LettersFragment.kt` | 305 | the notification-access message reads `perm_notification_body` (upstream's same words), which iori overrides |
| `app/src/main/java/com/github/gezimos/inkos/ui/notifications/NotificationsUI.kt` | 400 | the same, in Notifications |
| `app/src/main/java/com/github/gezimos/inkos/widget/QuoteWidget.kt` | 95 | 19 Nov: the quote widget's `"Hoog" -> Monospace` mapping, replaced by a marker comment |
| `app/src/main/res/values/strings.xml` | 274 | 19 Nov: `settings_font_hoog` removed; the XML comment in its place carries the marker |
| `app/src/main/res/xml/backup_rules.xml` | 2 | 19 Nov audit: Android's own backup (up to Android 11) takes nothing of Home's; the whole file is replaced (its other lines are in the carried table) |
| `app/src/main/res/xml/data_extraction_rules.xml` | 2 | 19 Nov audit: the same for cloud backup and phone-to-phone transfer (Android 12 and later); the whole file is replaced |
| `build.gradle.kts` | 6 | the AboutLibraries plugin, 12.2.4, `apply false` (15.x, which the other apps use, refuses upstream's Android Gradle Plugin 8.10) |

Upstream files changed on purpose with no markers: the carried commits (listed under "Not hooks"
below), and the two backup files replaced whole. The count is of non-blank added lines that are
not marked, as `git diff <base>` shows them; a merge that changes it is looked at by hand.

| File | Unmarked lines | What |
|---|---|---|
| `app/src/main/java/com/github/gezimos/inkos/data/Prefs.kt` | 4 | Folders (`main`): the folders pref |
| `app/src/main/java/com/github/gezimos/inkos/data/repository/AppsRepository.kt` | 15 | Folders |
| `app/src/main/java/com/github/gezimos/inkos/helper/CalendarEventsHelper.kt` | 12 | `upstream-fixes`: all-day events by their date |
| `app/src/main/java/com/github/gezimos/inkos/services/ActionService.kt` | 15 | `main`: lock-screen music |
| `app/src/main/java/com/github/gezimos/inkos/services/NotificationManager.kt` | 44 | `upstream-fixes`: honour `canShowBadge()` |
| `app/src/main/java/com/github/gezimos/inkos/services/NotificationService.kt` | 2 | `upstream-fixes`: honour `canShowBadge()` |
| `app/src/main/java/com/github/gezimos/inkos/ui/AppsDrawerState.kt` | 2 | Folders |
| `app/src/main/java/com/github/gezimos/inkos/ui/AppsFragment.kt` | 141 | Folders (with its house-style delete) |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/AppsUI.kt` | 15 | Folders (the E-ink mode lines are hooks above) |
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/HomeUI.kt` | 20 | `upstream-fixes`: all-day events by their date (`formatEventDate`) |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | 3 | Folders (the house dialogs are hooks above) |
| `app/src/main/res/values/strings.xml` | 16 | Folders' strings and plurals (lines 25-38: `folder` … `folder_with_count`) and the `upstream-fixes` `events_*` strings (2). The folder strings are part of Folders: a conflict here is resolved by keeping both sides |
| `app/src/main/res/xml/accessibility_service_config.xml` | 7 | `main`: lock-screen music |
| `app/src/main/res/xml/backup_rules.xml` | 8 | the whole file, replaced (hook at line 2) |
| `app/src/main/res/xml/data_extraction_rules.xml` | 11 | the whole file, replaced (hook at line 2) |

Upstream files removed whole on 19 Nov (decisions), so there is no line to mark: each came with
inkOS with no licence (see `CREDITS.md`). At the next merge, a conflict on any of these is
resolved by keeping it deleted, and a new upstream file of the same kind is checked the same way.

| Removed | What it was |
|---|---|
| `app/src/main/res/font/hoog.ttf` | the Hoog font ("hooge 05_53", Craig Kroeger; no licence, not OFL) |
| `app/src/main/res/raw/ofl_license.txt` (one line) | `- Hoog` taken out of its list of OFL fonts, which it never was; not marked, since the file is shown as it is in the app |
| `app/src/main/res/drawable/{bamboo,blossom3,building,forrest,halfdots,halfdots_two,japan2,japan_left,leaf,liberty,nyc_left,sf_left,trees_left,urban}.png` | the fourteen picture wallpapers packed in the APK |
| `app/src/main/res/play_store.png`, `play_store_features.png` | inkOS's store pictures |
| `fastlane/` | inkOS's store listing: texts, screenshots, icon, changelogs (with the unmarked `101014.txt`) |
| `Gemfile`, `Gemfile.lock` | fastlane's Ruby tooling, for the store listing above; unused without it (inkOS has not changed either since its first commit, so a merge is unlikely to meet them) |
| `wallpapers/` | inkOS's wallpaper pictures, not packed in the APK |
| `others/bmc-button.png` | inkOS's Buy Me a Coffee button picture |
| `docs/img/**/*.png`, `*.jpg`, `*.mp4` | inkOS's website screenshots, mockups and videos; the website's HTML under `docs/` is kept and its pictures no longer load; `docs/README-inkOS.md` links to inkOS's repository for screenshots instead |

Not hooks, but carried on top of v0.6:

- **Folders in the app drawer** (`data/Folders.kt`, and changes inside `ui/AppsFragment.kt`,
  `ui/compose/AppsUI.kt`, `ui/AppsDrawerState.kt`, `ui/dialogs/ComposeDialogManager.kt`,
  `data/Prefs.kt`, `data/repository/AppsRepository.kt`, and the folder strings and plurals in
  `res/values/strings.xml`, lines 25-38): these are edits to upstream's drawer, not one-line
  hooks, and will need care at the next upstream merge. The carried table above counts their
  lines; a conflict in `strings.xml` near `rename` is resolved by keeping both sides.
- **Your apps** (one page since 19 Nov, "decisions": the earlier Ecosystem apps page and its
  GitHub-based installer are gone, merged into this one). Settings → Apps → Your apps
  (`IoriYourApps.kt` the page, `IoriAppInstaller.kt` the work, `IoriAppsManifest.kt` the list and
  the two decisions, pure Kotlin with its tests; `IoriApps.kt` Home's own list): the house's apps
  from `https://wanderthe.dev/apps.json` (made by `tools/apps-manifest.py` in the site's
  repository; a copy is kept in Home's files for when there is no connection), in a folder for
  each section (Core, then Outdoors, On the road, Daily life, Music, Reading and writing, Games
  and oracles, Tools). The sections come from Home's own list, `assets/iori_apps.json` (from
  `tools/make-apps-list.py`, `SECTIONS` there, which stops on an app with no section); an app
  that list does not name goes under Core when the site marks it core, else under the last
  heading. A folder row says how many of its apps are on the phone ("3 of 5"), or in bold how
  many can be updated or are being got. In a folder each app has what it is, its version or
  "Not installed", how an install is going or why it failed, and Install / Update / Up to date.
  With no list from the site, fetched or kept (a first open offline, or the site down), the page
  shows Home's own list instead, in the same folders, each app with its version on the phone or
  "Not installed" and no Install or Update (Home's list has no download address, size or
  checksum), under a note that nothing can be installed or updated until the site's list comes,
  and Try again (`IoriAppInstaller.ListState.of`: fetched, else kept, else Home's own). An app
  asked for elsewhere then (first run's GET, the hand-off offer, the lines list) says "Cannot get
  it now", and the first run's page says why.
  An app is downloaded to Home's cache and handed to Android's PackageInstaller only when its
  size, SHA-256, package and version code are the list's, it is signed with the list's
  certificate and no other, that certificate is the one Home pins for the app
  (`assets/iori_apps.json` and `IoriAccount.CALLERS`; an app with no pin is not shown and never
  installed, so the list on the site cannot swap a key or bring in a new app), and an update is
  signed like the app on the phone. Android 12+ updates apps Home installed with no question. The
  file is deleted after; one left by a killed download is cleared when the list is next read.
  This is Home's only installer: the first run, the hand-off offer (`IoriHandOff`) and the lines
  list's Get (`LinesDialog`) call `IoriAppInstaller.install(context, pkg)`, which reads the list
  first when it is not in hand and fails with "Not in the list" for an app it does not have.
  Its only lines in upstream's files are the two manifest blocks in the ledger (the
  permissions, and `IoriInstallReceiver`);
  `IoriInstallReceiver` (now in `IoriAppInstaller.kt`) hands every answer to `IoriAppInstaller`.
  The first run (`IoriFirstRun.kt`) has the same apps as its third page, Your apps, a page of
  tick boxes (`IoriPickApps.kt`, from Home's own list, so nothing is asked of the network until
  GET), ahead of Theme and home apps so that what is got there can be put on the home screen:
  the core apps that are missing come ticked, GET (a button under the list, never in the place of
  NEXT, so a press meant for NEXT cannot start a download) installs the ticked ones one after the other
  through the same checked path (`IoriAppInstaller.installAll`), and an install question put
  away without an answer is given up when the page is back in front (`IoriAppInstaller.resumed`),
  so the ones behind it are not held up.

- **House style on what Home adds, inkOS underneath** (14 Nov): inkOS's home, drawer, A-Z
  letters, Settings pages and first-run look stay inkOS's. Home's own dialogs take the house type
  scale (`ui/HouseType.kt`, nothing under 16 sp; written out here because Home does not carry
  MMD) and the house list with its scrollbar (`ui/PagedColumn.kt` and `ui/EInkDialog.kt` are the
  shop's shared files; the four chevrons are MMD's, Apache-2.0, as `iori_chevron_*.xml`).
  Deleting a folder asks in its own row ("Delete folder — tap again"), in Settings and in the
  drawer. About Home's Licenses drawer is made by the build: Home's own lines, the full GPL, then
  one small drawer per library from `res/raw/aboutlibraries.json` (`ui/LicencesJson.kt`, the
  shop's reader), with its license, address and full text (`src/iori/res/raw`: Apache-2.0, and
  Glide's and protobuf's own BSD files; `IoriAbout.textsFor`). What Gradle cannot see is in
  `config/libraries`: MMD's chevrons, and inkOS's nine OFL fonts (`font-*.json`, the copyright
  line from each font file as its author line; the full text is inkOS's own
  `res/raw/ofl_license.txt`). Hoog (`hoog.ttf`, hooge 05_53 by Craig Kroeger) came with no
  licence and is not OFL: since 19 Nov it is not in Home at all (see the removed files above),
  nor are the picture wallpapers and store pictures that came with inkOS without one.
  `CREDITS.md` lists all of it.
- **README** (19 Nov): Home's own. Upstream's is kept, links fixed for its place, as
  `docs/README-inkOS.md`, so a merge that touches upstream's README conflicts only in
  `README.md`: take ours, and copy upstream's new one there.
- **Default apps** (14 Nov, `IoriDefaults.kt`): a first-run page after Your apps (the first run
  is five pages now) and a Settings row. One row for each kind of thing one of Home's apps is
  meant to open (calls and texts, which are roles, and filling in passwords; contacts, calendar, email, alarms and timers,
  camera, gallery, music, files, text files, map links, calculator; and, as notes only, opening
  a picture, a song or a PDF, magnet and bitcoin links). A row is shown only when that app is on
  the phone and is one of the apps Android would offer. It says what Android would do today:
  ours, not chosen yet, or another app. A press on "not chosen" raises Android's own "open with"
  question (pick the app, then Always); where another app holds it, the press opens the screen
  where that is changed. The state is read again every time the page comes back in front.
  The Settings row is on Home's own top page (`IoriSettings.Top`), not in `SettingsFragment.kt`.
  Upstream's files gain only the SET_ALARM permission (in the manifest block, to ask the clock
  question), and `src/iori/res/xml/file_paths.xml` stands in for main's whole file and adds the
  folder of the sample text file handed to Android (see "Shadowed upstream code and resources").
  Filling in passwords is a row too: which autofill service is chosen is read from Android's
  settings, and the press opens Android's autofill screen with Passwords named (seen on API 31).
- **Launcher icon** (14 Nov): a small plain thatched hut, which is what iori means, in the
  stock Kompakt style of the other apps (81 grid, the stock ring, stroke 2.5).
  `src/iori/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` are plain vectors
  that stand in for upstream's adaptive icon; upstream's own icon files are untouched, and the
  mark inside the app (Settings header) is still inkOS's.
- **Home lines, Place on home** (14 Nov): Bottom (as before) or Top, under the date line; at the
  top the home apps' last row sits at the bottom of the screen (`IoriLinesPlace`, kept in iori's
  own settings, not in a Backup export).
  16 Nov: with the lines at the top and the media widget on, the player's room at the foot
  stays when nothing is playing, so the app list does not drop when the player goes away.

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
too. Main's `strings.xml` differs from upstream by the Folders strings and plurals (lines 25-38),
the `upstream-fixes` `events_*` strings and the removed `settings_font_hoog` (all in the ledger).
If upstream rewords, renames or re-placeholders one of the overridden strings,
`tools/iori-shadows.py --to vX.Y` lists it, and the new wording is carried over by hand. Upstream's words that are
written in code, not in strings.xml, go through `IoriNames.named`, which turns "inkOS" as a word on
its own into "Home" and leaves links alone; it is used only where the words mean this app,
never on a credit to inkOS or gezimos. Only the English
default is overridden; another locale's upstream translation still says inkOS.

The real inkOS (`app.inkos`) installed beside iori stays out of iori's Recents and usage lists,
on purpose: upstream's own filter (RecentsFragment.kt:670-671, MainViewModel.kt:329) leaves it
out, a launcher is not something to go back to from Recents, and keeping upstream's lines as
they are keeps those files out of the carried diff. Everywhere else it is an ordinary app.

## Shadowed upstream code and resources

Some of upstream's code and resources Home hides without changing a line of them. A merge then
brings upstream's changes there in with no conflict, and Home never runs or shows them.
`tools/iori-shadows.py` lists and checks all of it (merge steps 2 and 7); run as it is, it must
say "no problems".

**Strings.** `app/src/iori/res` is added to the debug and release resources, which outrank
main's, so a string, plurals or string-array of the same name there replaces upstream's English
one: 270 in `iori_upstream_settings.xml` (inkOS's Settings words in the house voice: sentence
case, notes that only restate their label emptied) and 22 in `iori_strings.xml` (listed under
"String overrides" above). The script checks that each one's placeholders (`%s`, `%1$d` ...) match
upstream's and that upstream still has the name; with `--to vX.Y` it lists the ones upstream
reworded, renamed, dropped or gave new placeholders since the base. Another locale is upstream's
as it is.

**Whole files.** A file at the same path in `app/src/iori/res` replaces upstream's whole file,
not entry by entry:

| Upstream's file | Home's copy is |
|---|---|
| `app/src/main/res/xml/file_paths.xml` | the FileProvider's paths: upstream's `crash_logs/` and Home's `iori_samples/` (the sample text file `IoriDefaults` hands to Android). A path upstream adds must be copied in, or its `FileProvider.getUriForFile` fails at run time ("Failed to find configured root"); the script checks that every entry of upstream's is in Home's copy |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Home's launcher icon (see "Launcher icon" above) |

**Code Home goes around.** A hook returns before these run, or calls Home's own in their place,
so a fix upstream makes inside them never reaches Home. The sheets they open in
`ComposeBottomSheet.kt` are reached only through them, so read those too when one is listed.

| File | Function | Home's own |
|---|---|---|
| `app/src/main/java/com/github/gezimos/inkos/ui/compose/OnboardingScreen.kt` | `Show` | `IoriFirstRun.Show`, called at OnboardingScreen.kt:79 in its place. A page upstream adds, or a pref one of its pages sets that other code relies on (a new permission page, say), has to be added to Home's first run |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | `showSliderDialog` | `IoriDialogs.number` (hook at line 109) |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | `showSingleChoiceDialog` | `IoriDialogs.choose` (line 140), unless the caller passes `onItemDeleted` (none does today) |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | `showTabbedFontPicker` | `IoriDialogs.fonts` (line 175) |
| `app/src/main/java/com/github/gezimos/inkos/ui/dialogs/ComposeDialogManager.kt` | `showGestureActionPicker` | `IoriDialogs.gesture` (line 223) |

Upstream's Settings pager is not in this table: Home deleted its ~60 lines in place
(`SettingsFragment.kt`, ledger rows 403-426) for `IoriSettingsPages`, so an upstream change to
it conflicts in the merge; port it into `IoriSettingsPages` when it does.

## Identity

`app/iori.gradle`, applied last from `app/build.gradle.kts`:

- reads upstream's `defaultConfig.versionCode` first and keeps it as
  `BuildConfig.UPSTREAM_VERSION_CODE`, so the field always equals the base it was built on;
  `app/build.gradle.kts` therefore keeps upstream's own `versionCode` and `versionName`
  (101013, "0.6" at v0.6) and is never bumped for Home. An unmarked bump to 101014 / "0.6.1"
  (bf44981, with a v0.6.1 entry in upstream's `CHANGELOG.md` and a fastlane changelog) was undone
  on 19 Nov; `CHANGELOG.md` is upstream's again. No guard was needed for a phone that ran a
  101014 build: the only reader is `Migration.migratePreferencesOnVersionUpdate`, run at each
  start, whose cleanups fire only for versions in `saved + 1 .. current` (empty when 101014 goes
  back to 101013) and whose first-open step only for a saved code up to 101004; it then writes
  101013 back, so upstream's real next migrations fire as they should;
- sets `applicationId` `com.wanderwildwood.iori` (debug keeps upstream's `.debug` suffix),
  `versionCode` 2, `versionName` 1.0.0 (test builds on phones were 1 / 0.1.0), and `app_name`
  "Home" ("Home Debug");
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
2. While the ledger still names the old base: `tools/iori-shadows.py --to vX.Y`. It lists what
   upstream changed in what Home shadows: overridden strings reworded, renamed or with new
   placeholders, whole files Home's copy stands in for, and the code Home goes around. Keep
   the list for step 7.
3. `git checkout upstream-fixes && git merge vX.Y` (rerere is on: `git config rerere.enabled true`)
4. `git checkout main && git merge upstream-fixes`
5. Re-place any hook the merge moved. A conflict on a marked line, or inside an
   `iori: begin`/`iori: end` block, keeps Home's change, with upstream's change carried into it.
6. Check that `Iori.guardCrashes` still follows the line where MainActivity puts CrashHandler
   back, and that `UPSTREAM_VERSION_CODE` in the built BuildConfig equals the new base's code.
7. Port what step 2 listed into Home's own (`IoriFirstRun`, `IoriDialogs`, `IoriSettingsPages`,
   the iori strings, `src/iori/res/xml/file_paths.xml`), then run
   `tools/iori-shadows.py --from vX.Y`: it must say "no problems".
8. Set `Upstream base:` in the ledger to `vX.Y`, write the new line numbers into the table
   (`tools/iori-hooks.py --list`) and the carried counts, and run `tools/iori-hooks.py`: it must
   say "ledger matches the tree". `git diff --stat vX.Y..main -- ':!app/src/iori'
   ':!app/iori.gradle' ':!IORI.md'` then lists only the ledger's files, the carried files and the
   files the carried commits added.

Never rebase a branch that has been pushed. The inherited `release.yml` and `build.yml` stay in
the tree, switched off; deleting them would conflict on every merge.

## Publishing (first time)

Staged, not done: `wanderwildwood/iori` exists on GitHub but is private, with Actions off
(`"enabled": false`), Issues on, Wiki off, the four topics set, and only an older `main`
pushed. No tag has been pushed and there is no release. What is left, in this order:

**Before anything reaches GitHub**

Builds are local; GitHub must never run upstream's workflows. This clone carries two, in
`.github/workflows/`:

- `build.yml` ("Build APK"): runs on a push or pull request to `main`, and by hand.
- `release.yml` ("Build and Release APK"): runs on any `v*` tag, and by hand. It would build
  with upstream's settings and publish a release of its own.

This clone also holds upstream's tags `v0.1` to `v0.6`, so a stray `--tags` (or `--mirror`)
would set `release.yml` off six times once Actions is on.

1. The `main` already on GitHub holds an unscrubbed line, and GitHub keeps a force-pushed-over
   commit reachable by its SHA, so the private repository is deleted and made again before the
   push: `gh repo delete wanderwildwood/iori --yes`, `gh repo create wanderwildwood/iori
   --private` with the same description, then at once `gh api -X PUT
   repos/wanderwildwood/iori/actions/permissions -F enabled=false` (check: `"enabled": false`)
   and the settings of step 6.
2. The personal-data scrub: older commits still carry personal comments that the tree no
   longer has, so the history is rewritten with `git filter-repo` on a copy, checked, and
   that copy is what is pushed (the staged copy and its exact commands are with the release
   staging). `gitleaks detect` over the tree and the history, and `git log upstream/main..main`
   read once more for anything that should not go out. After the rewrite every local branch
   and worktree is re-pointed at the rewritten commits or made again from them.

**Pushing**

3. `main` is fast-forwarded to the release commit, in the rewritten copy. Then `git push
   origin main`, and any other public branch by name. Never `--tags`, `--follow-tags`, `--all`
   or `--mirror`; iori's own tags go one by one, by name.
4. `gh api -X PATCH repos/wanderwildwood/iori -f visibility=public`.
5. `gh workflow disable build.yml -R wanderwildwood/iori` and the same for `release.yml`,
   now that GitHub knows them. The files stay in the tree, so merges stay clean.
6. Repository settings: Issues on, Wiki and Projects off, topics
   `mudita-kompakt`, `eink`, `launcher`, `inkos`. There is no Sponsor button: upstream's
   `.github/FUNDING.yml` is removed here, and the cup in Settings is where gezimos's Buy Me a
   Coffee lives.

**The release**

7. Build the release locally at the pushed `main`. Check the APK is signed with iori's
   release key (SHA-256 `fb06881da0a2c7b0…`, the one Glance's `PanelForHome` knows) and that
   `aapt2 dump badging` says `com.wanderwildwood.iori`, `versionName='1.0.0'`,
   `versionCode='2'`. The APK built from the commit before the rewrite has the same tree, so
   it stands.
8. Assets, named as the other apps' releases are (kotozute v1.35.45: `kotozute.apk`,
   `kotozute.apk.sha256`, `kotozute-v1.35.45.apk`, `kotozute-v1.35.45.apk.sha256`):
   `iori.apk`, `iori.apk.sha256`, `iori-v1.0.0.apk`, `iori-v1.0.0.apk.sha256`. Each `.sha256`
   is `sha256sum` output naming its own file. The README's download link is
   `releases/latest/download/iori.apk`, so `iori.apk` must be there.
9. `git tag -a v1.0.0 -m "Home 1.0.0"` on that commit, `git push origin v1.0.0`
   (that one tag, by name), then `gh release create v1.0.0 --repo wanderwildwood/iori
   --title "Home 1.0.0" --notes-file <notes>` with the four assets. Notes below.

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
13. Upstream fixes: the two fix commits (all-day events by their date; honour
    `canShowBadge()`) can go to gezimos as issue patches, each on its own.

### Release notes, v1.0.3

> # Home 1.0.3
>
> - **KompaktOS:** an app's E-ink mode opens the Kompakt app, where KompaktOS keeps the modes, and the e-ink refresh gesture asks KompaktOS for a full refresh. On first start Home shows the real status bar and leaves the swipe down to the system's notification shade; a backup restored from MuditaOS keeps both.
> - **Restricted settings:** on Android 13 and later, when Home was installed from a file, Home says how to let it read notifications or use Accessibility: App info, then ⋮, then Allow restricted settings.

### Release notes, v1.0.2

> # Home 1.0.2
>
> - **Your apps:** each app in a folder is now just its name and its version, so a page holds about seven apps on the Kompakt. The one-line description, cut short, said too little to keep.

### Release notes, v1.0.1

> # Home 1.0.1
>
> - **Your apps:** a folder now shows several apps to a page (five on the Kompakt) instead of one. Each app takes three short lines: its name, what it is, and its version, or for an update "0.1.36 to 0.1.39". A long name or description is cut off rather than wrapped.

### Release notes, v1.0.0

> # Home 1.0.0
>
> Home is a plain, quiet home screen for E Ink phones, made on the Mudita Kompakt. It is
> [inkOS](https://github.com/gezimos/inkos) by gezimos, as its own app beside inkOS, with what
> the apps made to work with it need. Everything inkOS does still works, and a backup from inkOS
> restores into Home.
>
> - **One Nextcloud account for the whole phone.** Sign in once, in the first run or in
>   Settings → Nextcloud account. The apps that sign in to Nextcloud get a row "Use the phone's
>   Nextcloud account": one tap there, and nothing to type. The password is locked on the phone
>   and handed only to those apps, checked by their signing key. Signing out in Home signs them
>   out too and deletes the password on the server.
> - **Your apps.** Settings → Your apps lists the apps made to work with Home, in folders (Core
>   first), each one installed or missing, with Install and Update. A download is installed only
>   if its size, checksum, package, version and signing key are the right ones. With no
>   connection it shows the list Home carries, without installing.
> - **Settings in groups**: Phone, Apps, Home screen, Setup and about, in the same plain look as
>   the other apps. inkOS's own pages and rows are all still there.
> - **Dark mode for all the apps.** On MuditaOS, Settings → Dark mode turns Home dark, and the
>   other apps follow it. On other phones, Home follows the phone's own dark setting.
> - **A first run of its own**: welcome and privacy, setup, your apps, default apps, Nextcloud,
>   theme and home apps. Settings → Run setup again opens it again; nothing is reset.
> - **Default apps.** One row for each kind of thing the apps open: calls, texts, contacts,
>   calendar, alarms, gallery, maps, text files, passwords and more. Each row says what Android
>   does today.
> - **Lines on home.** Up to three lines under the home apps from
>   [Glance](https://github.com/wanderwildwood/hitome) 0.1.24 or later: today's events, the
>   weather, what is unread. A press opens the app the line came from; a long press shows each
>   source and what it is doing. The lines can sit at the top or at the bottom.
> - Also: music on the lock screen for any player, folders in the app drawer, E-ink mode per app
>   on MuditaOS 2.0 and later, a contact found in search opens in Connections, all-day events
>   show on their own date, and service notifications no longer badge their apps.
>
> Unlike inkOS, Home has the internet permission, used only for Your apps and for your own
> Nextcloud server. Home has no server of its own and sends nothing about you.
>
> 1.0.0 installs over a 0.1.0 test build and keeps its settings.
>
> Nearly all of Home is inkOS, by [gezimos](https://github.com/gezimos), under the GPL-3.0.
> Thank you. For plain inkOS, go to [gezimos's repository](https://github.com/gezimos/inkos).

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
