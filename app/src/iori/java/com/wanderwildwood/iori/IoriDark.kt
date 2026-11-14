package com.wanderwildwood.iori

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import com.github.gezimos.inkos.BuildConfig
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsHomeItem

/**
 * Dark mode for the whole phone: one switch in Home's Settings.
 *
 * Home itself goes dark with inkOS's own dark theme. The other apps of this shop each have a
 * dark mode switch of their own, and each asks Home (the provider below) what this switch
 * says when it comes to the front. An app takes the answer when the switch has been pressed
 * since it last asked, so pressing here turns them all, and a switch pressed later inside one
 * app still holds for that app.
 *
 * The provider gives out two numbers and nothing else: dark or not, and when the switch was
 * last pressed. Any app may read them; none can write.
 */
object IoriDark {

    private const val KEY_ON = "dark_all"
    private const val KEY_STAMP = "dark_all_stamp"

    fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.dark/state")

    fun on(context: Context): Boolean = Iori.prefs(context).getBoolean(KEY_ON, false)

    fun stamp(context: Context): Long = Iori.prefs(context).getLong(KEY_STAMP, 0L)

    /** The switch: remembers it, tells the apps that are open, and turns Home itself. */
    fun set(context: Context, dark: Boolean, setHomeTheme: (Constants.Theme) -> Unit) {
        Iori.prefs(context).edit().putBoolean(KEY_ON, dark).putLong(KEY_STAMP, System.currentTimeMillis()).commit()
        runCatching { context.contentResolver.notifyChange(uri(context), null) }
        setHomeTheme(if (dark) Constants.Theme.Dark else Constants.Theme.Light)
        AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        (context as? Activity)?.recreate()
    }

    @Composable
    fun SettingsRow(titleFontSize: TextUnit, setHomeTheme: (Constants.Theme) -> Unit) {
        val context = LocalContext.current
        var dark by remember { mutableStateOf(on(context)) }
        SettingsHomeItem(
            title = stringResource(R.string.iori_dark_title),
            imageVector = Icons.Rounded.Contrast,
            description = stringResource(if (dark) R.string.iori_dark_on else R.string.iori_dark_off),
            titleFontSize = titleFontSize,
            onClick = {
                dark = !dark
                set(context, dark, setHomeTheme)
            },
        )
    }
}

/** What the other apps read: one row, `dark` (0 or 1) and `stamp`. Read-only. */
class IoriDarkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null
        return MatrixCursor(arrayOf("dark", "stamp")).apply {
            addRow(arrayOf<Any>(if (IoriDark.on(context)) 1 else 0, IoriDark.stamp(context)))
            setNotificationUri(context.contentResolver, IoriDark.uri(context))
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
