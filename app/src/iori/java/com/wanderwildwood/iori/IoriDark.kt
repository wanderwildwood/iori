package com.wanderwildwood.iori

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import com.github.gezimos.inkos.data.Prefs
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
 *
 * Where the phone's system has a dark setting of its own ([systemHasDarkSetting]: KompaktOS,
 * not MuditaOS), that setting is the one switch instead. Home's row is not shown, Home's own
 * theme follows the system's ([atStart], [systemChanged]), and the provider answers "not dark"
 * whatever the switch was left at ([answer]), since each app follows the system by itself and
 * a switch left on here would otherwise hold them all dark.
 */
object IoriDark {

    private const val KEY_ON = "dark_all"
    private const val KEY_STAMP = "dark_all_stamp"

    fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.dark/state")

    /** Android's "dark theme settings" screen; the platform keeps the name of this action to itself. */
    private const val DARK_THEME_SETTINGS = "android.settings.DARK_THEME_SETTINGS"

    /**
     * Whether the phone's system has a dark setting of its own: its Settings app answers
     * Android's "dark theme settings" screen. The one place this is decided.
     */
    fun systemHasDarkSetting(context: Context): Boolean = try {
        context.packageManager.resolveActivity(Intent(DARK_THEME_SETTINGS), 0) != null
    } catch (_: Exception) {
        false
    }

    /** What the provider says for `dark`: Home's switch, and never dark where the system has its own setting. */
    fun answer(switchOn: Boolean, systemHasSetting: Boolean): Int = if (switchOn && !systemHasSetting) 1 else 0

    /** The system's night setting, read from the application, which AppCompat's own override does not reach. */
    private fun systemNight(context: Context): Boolean =
        context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** Home's theme for the system's setting, where there is one and Home is not on it yet; else null. */
    private fun owed(context: Context): Constants.Theme? {
        if (!systemHasDarkSetting(context)) return null
        val wanted = if (systemNight(context)) Constants.Theme.Dark else Constants.Theme.Light
        return wanted.takeIf { Prefs(context).appTheme != it }
    }

    /** As Home starts, before it reads its theme: the system's dark setting, where it has one, is Home's. */
    fun atStart(context: Context, setHomeTheme: (Constants.Theme) -> Unit) {
        owed(context)?.let(setHomeTheme)
    }

    /** The system's setting changed while Home was up: Home turns with it, as a press on its own switch would. */
    fun systemChanged(activity: Activity, setHomeTheme: (Constants.Theme) -> Unit) {
        val theme = owed(activity) ?: return
        setHomeTheme(theme)
        AppCompatDelegate.setDefaultNightMode(if (theme == Constants.Theme.Dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        activity.recreate()
    }

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
        // The system's own dark setting is the switch, where it has one.
        if (remember { systemHasDarkSetting(context) }) return
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

/** What the other apps read: one row, `dark` (0 or 1, see [IoriDark.answer]) and `stamp`. Read-only. */
class IoriDarkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null
        return MatrixCursor(arrayOf("dark", "stamp")).apply {
            addRow(arrayOf<Any>(IoriDark.answer(IoriDark.on(context), IoriDark.systemHasDarkSetting(context)), IoriDark.stamp(context)))
            setNotificationUri(context.contentResolver, IoriDark.uri(context))
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
