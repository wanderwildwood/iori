package com.wanderwildwood.iori

import com.github.gezimos.inkos.BuildConfig

/**
 * Makes an inkOS backup restore into iori with its internal slots still working.
 *
 * inkOS stores its own entries under its own package name: a home slot holding the app drawer
 * or another internal shortcut is `"app.inkos"`, and hidden or locked internal entries start
 * `"app.inkos|"`. Restored as they are, those would point at inkOS, and pressing such a slot
 * would open the other launcher, or nothing if it is not installed.
 *
 * Only those two quoted forms change. A key that merely contains the name, such as
 * `app_alias_app.inkos`, or a backup from inkOS's debug build (`app.inkos.debug`) is left as it
 * was, because neither starts with the quote.
 */
object IoriBackup {

    private const val INKOS = "app.inkos"

    fun rewrite(json: String, ownPackage: String = BuildConfig.APPLICATION_ID): String {
        if (ownPackage == INKOS) return json
        return json
            .replace("\"$INKOS\"", "\"$ownPackage\"")
            .replace("\"$INKOS|", "\"$ownPackage|")
    }
}
