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
import android.os.Build
import android.provider.Settings
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
 * The provider gives out two numbers and nothing else: dark or not, and a stamp. The stamp moves
 * whenever the answer does: when the switch is pressed, and when the answer changes by itself
 * ([given]). Any app may read them; none can write.
 *
 * The switch is only on MuditaOS, which has no dark setting a person can reach. Anywhere else
 * ([systemHasDarkSetting]: KompaktOS, any other Android from 10 on) the system's own dark setting
 * is the one switch: Home's row only opens it, Home's own theme follows it ([atStart],
 * [systemChanged]), and the provider answers "not dark" whatever the switch was left at
 * ([answer]). Each app follows the system by itself, and a switch left on under MuditaOS before
 * the phone moved to KompaktOS would otherwise hold them all dark. Home has no switch there to
 * press, so the answer turning from dark to not dark moves the stamp itself ([given]), and each
 * app hears it the next time it comes to the front and asks.
 */
object IoriDark {

    private const val KEY_ON = "dark_all"
    private const val KEY_STAMP = "dark_all_stamp"

    /** The answer the provider last gave out (0 or 1), so a change is seen across restarts. */
    private const val KEY_GIVEN = "dark_all_given"

    fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.dark/state")

    /** Android's "dark theme settings" screen; the platform keeps the name of this action to itself. */
    private const val DARK_THEME_SETTINGS = "android.settings.DARK_THEME_SETTINGS"

    /** Mudita's own Settings app, which MuditaOS has in place of Android's Settings screens. */
    private const val MUDITA_SETTINGS = "com.mudita.settings"

    /**
     * Whether the phone's system has a dark setting a person can reach: anything but MuditaOS,
     * from Android 10 on. The one place this is decided.
     *
     * Asking Settings for its dark-theme screen cannot tell: MuditaOS keeps Android's screen
     * installed, so the question is answered there too, but its own Settings never leads to it.
     * So it goes by which system this is ([isMuditaOs]).
     */
    fun systemHasDarkSetting(context: Context): Boolean = hasDarkSetting(Build.VERSION.SDK_INT, isMuditaOs(context))

    /** The rule itself, apart from the phone: Android 10 brought the system's dark setting; MuditaOS hides it. */
    fun hasDarkSetting(sdk: Int, muditaOs: Boolean): Boolean = sdk >= 29 && !muditaOs

    /**
     * MuditaOS: Mudita's Settings app is on the phone, and the system is not LineageOS.
     * KompaktOS is LineageOS on the same phone, so the maker's name says nothing; it is told
     * apart by Lineage's own marks, in case Mudita's Settings is ever found there too.
     */
    fun isMuditaOs(context: Context): Boolean = muditaOs(installed(context, MUDITA_SETTINGS), lineage)

    fun muditaOs(muditaSettings: Boolean, lineage: Boolean): Boolean = muditaSettings && !lineage

    private fun installed(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    /** LineageOS names itself in the build's display id and in its own system properties. Read once. */
    private val lineage: Boolean by lazy {
        Build.DISPLAY.contains("lineage", ignoreCase = true) ||
            prop("ro.lineage.version").isNotEmpty() ||
            prop("ro.lineage.build.version").isNotEmpty()
    }

    private fun prop(name: String): String = try {
        val p = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().use { it.readText() }.trim().also { p.waitFor() }
    } catch (_: Exception) {
        ""
    }

    /** Opens the system's dark setting: its own screen, or the display settings, or Settings. */
    fun openSystemDarkSetting(context: Context) {
        val tries = listOf(DARK_THEME_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS)
        for (action in tries) {
            val intent = Intent(action).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
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

    /**
     * As Home starts, before it reads its theme: the system's dark setting, where it has one, is
     * Home's. It also moves the provider's stamp now if its answer changed ([given]), so the apps
     * that are open hear it without waiting to ask.
     */
    fun atStart(context: Context, setHomeTheme: (Constants.Theme) -> Unit) {
        owed(context)?.let(setHomeTheme)
        given(context)
    }

    /** The system's setting changed while Home was up: Home turns with it, as a press on its own switch would. */
    fun systemChanged(activity: Activity, setHomeTheme: (Constants.Theme) -> Unit) {
        val theme = owed(activity) ?: return
        setHomeTheme(theme)
        AppCompatDelegate.setDefaultNightMode(if (theme == Constants.Theme.Dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        // Not in the middle of a first run: inkOS opens the first run again on every start while it
        // is unfinished, so a restart there would stack a second one under the first. The pages
        // keep the look they have, and Home has the system's from the home screen on.
        if (!Prefs(activity).firstOpen) activity.recreate()
    }

    fun on(context: Context): Boolean = Iori.prefs(context).getBoolean(KEY_ON, false)

    fun stamp(context: Context): Long = Iori.prefs(context).getLong(KEY_STAMP, 0L)

    /**
     * What the provider gives out: the answer and its stamp. When the answer is not the one last
     * given out (the phone moved from MuditaOS to a system with its own dark setting, with the
     * switch left on), the stamp moves and the open apps are told, as a press would. Before this
     * was kept, the last answer given was the switch itself.
     */
    @Synchronized
    fun given(context: Context): Pair<Int, Long> {
        val prefs = Iori.prefs(context)
        val switchOn = on(context)
        val now = answer(switchOn, systemHasDarkSetting(context))
        val last = prefs.getInt(KEY_GIVEN, if (switchOn) 1 else 0)
        if (now != last || !prefs.contains(KEY_GIVEN)) {
            val edit = prefs.edit().putInt(KEY_GIVEN, now)
            if (now != last) edit.putLong(KEY_STAMP, System.currentTimeMillis())
            edit.commit()
            if (now != last) runCatching { context.contentResolver.notifyChange(uri(context), null) }
        }
        return now to stamp(context)
    }

    /** The switch: remembers it, tells the apps that are open, and turns Home itself. */
    @Synchronized
    fun set(context: Context, dark: Boolean, setHomeTheme: (Constants.Theme) -> Unit) {
        Iori.prefs(context).edit()
            .putBoolean(KEY_ON, dark)
            .putLong(KEY_STAMP, System.currentTimeMillis())
            .putInt(KEY_GIVEN, answer(dark, systemHasDarkSetting(context)))
            .commit()
        runCatching { context.contentResolver.notifyChange(uri(context), null) }
        setHomeTheme(if (dark) Constants.Theme.Dark else Constants.Theme.Light)
        AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        (context as? Activity)?.recreate()
    }

    @Composable
    fun SettingsRow(titleFontSize: TextUnit, setHomeTheme: (Constants.Theme) -> Unit) {
        val context = LocalContext.current
        // The system's own dark setting is the switch, where it has one: the row only opens it.
        if (remember { systemHasDarkSetting(context) }) {
            SettingsHomeItem(
                title = stringResource(R.string.iori_dark_title),
                imageVector = Icons.Rounded.Contrast,
                description = stringResource(if (systemNight(context)) R.string.iori_dark_system_on else R.string.iori_dark_system_off),
                titleFontSize = titleFontSize,
                onClick = { openSystemDarkSetting(context) },
            )
            return
        }
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

/** What the other apps read: one row, `dark` (0 or 1, see [IoriDark.answer]) and `stamp` ([IoriDark.given]). Read-only. */
class IoriDarkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = context ?: return null
        return MatrixCursor(arrayOf("dark", "stamp")).apply {
            val (dark, stamp) = IoriDark.given(context)
            addRow(arrayOf<Any>(dark, stamp))
            setNotificationUri(context.contentResolver, IoriDark.uri(context))
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
