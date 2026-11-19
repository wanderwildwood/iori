package com.wanderwildwood.iori

import android.content.Context
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Prefs

/**
 * What came with inkOS without a licence and is not in Home (19 Nov): the Hoog font and the
 * fourteen picture wallpapers (Japan, NYC, SF, Trees, Bamboo and the rest). A phone that had
 * chosen one of them comes back to something that is in Home:
 *
 *  - a font setting that names Hoog becomes inkOS's default font, Public Sans (inkOS would read
 *    it as the system font, without crashing, but Public Sans is what an unset choice gives);
 *  - a home wallpaper that was one of the pictures is taken away, with the copy of it Home kept,
 *    and the background is drawn solid, as a theme with no picture is. The pictures were the only
 *    wallpapers stored by a positive resource number: generated ones are negative and a picture
 *    of the person's own is stored as 0.
 *
 * Run at each start ([Iori.guardCrashes]), so a backup or theme restored with Hoog in it is put
 * right at the next start (until then inkOS reads the name it does not know as the system font);
 * it does nothing once nothing names them.
 */
object IoriRemoved {

    const val FONT = "Hoog"
    private const val WALLPAPER_ID = "INKOS_WALLPAPER_RESOURCE_ID"

    fun migrate(context: Context) {
        try {
            val prefs = Prefs(context)
            val sp = prefs.sharedPrefs
            val fonts = sp.all.filterValues { it == FONT }.keys
            if (fonts.isNotEmpty()) {
                val edit = sp.edit()
                fonts.forEach { edit.putString(it, Constants.FontFamily.PublicSans.name) }
                edit.apply()
            }
            if (removedWallpaper(WALLPAPER_ID, sp.all[WALLPAPER_ID])) {
                prefs.inkosWallpaperPath?.let { runCatching { java.io.File(it).delete() } }
                prefs.inkosWallpaperPath = null
                prefs.inkosWallpaperResourceId = 0
                prefs.backgroundOpacity = 255
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Whether [key] and [value], from Home's settings or from a theme being imported, name one of
     * the removed picture wallpapers. A theme file carries the wallpaper as a resource number,
     * which in this build would name some other resource; it is left out of the import.
     */
    fun removedWallpaper(key: String, value: Any?): Boolean =
        key == WALLPAPER_ID && ((value as? Number)?.toInt() ?: 0) > 0
}
