# Credits

Home is a fork of [inkOS](https://github.com/gezimos/inkos) by gezimos, and nearly all of it is
inkOS. This page lists whose work is in this repository and under which licence. The
libraries the app is built with are listed in the app itself: **Settings → About Home →
Licenses**, made by the build from each library's own licence.

## Code

| What | By | Licence |
|---|---|---|
| [inkOS](https://github.com/gezimos/inkos): the launcher, nearly all of `app/src/main` | gezimos | GPL-3.0 |
| [mLauncher](https://github.com/DroidWorksStudio/mLauncher), which inkOS was first forked from | DroidWorksStudio | GPL-3.0 |
| [Olauncher](https://github.com/tanujnotes/Olauncher), which mLauncher was forked from | tanujnotes | GPL-3.0 |
| [Katapult](https://github.com/gezimos/Katapult): how the lock-screen music strip works | gezimos | GPL-3.0 |
| Home's additions: `app/iori.gradle`, `app/src/iori/`, the lines marked `// iori:` | wander wildwood | GPL-3.0 only |

## Fonts that come with inkOS

In `app/src/main/res/font/`. Each carries its copyright line inside the file. The licence text
is `app/src/main/res/raw/ofl_license.txt`, shown in the app under inkOS's About → Font
license (OFL) and in About Home → Licenses.

| Font | Copyright | Licence |
|---|---|---|
| Iosevka 1.7.1 | Copyright (c) 2015-2016 Belleve Invis | OFL-1.1 |
| Manrope 4.504 | Copyright 2019 The Manrope Project Authors | OFL-1.1 |
| Merriweather 2.002 | Copyright 2016 The Merriweather Project Authors, Reserved Font Name "Merriweather" | OFL-1.1 |
| Plus Jakarta Sans 2.071 (italic) | Copyright 2020 The Plus Jakarta Sans Project Authors | OFL-1.1 |
| Public Sans 2.001 | Copyright 2015 The Public Sans Project Authors | OFL-1.1 |
| Short Stack 1.002 | Copyright (c) 2011, Sorkin Type Co, Reserved Font Names "Short Stack" and "Short Stack One" | OFL-1.1 |
| Space Grotesk 2.000 | Copyright 2020 The Space Grotesk Project Authors | OFL-1.1 |
| Vollkorn 5.001 | Copyright 2018 The Vollkorn Project Authors | OFL-1.1 |
| Zalando Sans Expanded 1.700 | Copyright 2025 The Zalando Sans Expanded Project Authors | OFL-1.1 |

## Pictures and other files

| What | From | Licence |
|---|---|---|
| The scrollbar chevrons of Home's lists (`app/src/iori/res/drawable/iori_chevron_*.xml`) | [Mudita Mindful Design](https://github.com/mudita/MMD) 1.0.2, Mudita Sp. z o.o., based on Material 3 | Apache-2.0 |
| `docs/`: inkOS's website (its pages and drawings, without its screenshots and videos) and `README-inkOS.md` | inkOS | not stated; `docs/fonts/material-symbols-outlined.woff2` is Google's Material Symbols, Apache-2.0 |
| Home's launcher icon, the llama and the coffee cup (`app/src/iori/res/`) | wander wildwood | GPL-3.0 only |
| `screenshots/` | wander wildwood, of Home | GPL-3.0 only |

Where this page says "not stated", inkOS's repository gives no source or licence for the file
beyond the project's own GPL-3.0.

## Left out

These came with inkOS with no licence of their own, and are not in Home (removed 19 Nov 2026):

- the Hoog font (`hoog.ttf`, "hooge 05_53", ©2001 Craig Kroeger, www.miniml.com): no licence
  came with it, and it is not under the OFL. A font setting that named it becomes Public Sans;
- the fourteen picture wallpapers the theme presets and the wallpaper list set
  (`app/src/main/res/drawable/*.png`: Japan, NYC, SF, Trees, Bamboo and the rest) and the
  pictures in `wallpapers/`. The themes that set one now have a plain background; a home
  wallpaper that was one of them is taken away;
- inkOS's store listing and promotional pictures: `fastlane/`, `play_store_features.png`,
  `app/src/main/res/play_store.png`, `others/bmc-button.png`, and the screenshots, mockups and
  videos of its website (`docs/img`).
