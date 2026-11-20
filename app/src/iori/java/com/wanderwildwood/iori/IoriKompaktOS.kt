package com.wanderwildwood.iori

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Prefs

/**
 * KompaktOS, the LineageOS 23.2 (Android 16) system for the Kompakt (github.com/gezimos/KompaktOS).
 * Everything here is asked through [isKompaktOS], so Home on MuditaOS and elsewhere is unchanged.
 *
 * What KompaktOS does that Home meets (read from its source, 1.2, 11-20):
 * - Its Kompakt app (package com.kompakt.service) owns the e-ink modes, the phone's and each
 *   app's, and gives other apps no way to set them. So Home sends people there.
 * - It answers an exported broadcast, com.kompakt.service.action.REFRESH, with a real full
 *   refresh of the panel, the same one as holding Home.
 * - Its notification shade is pulled from the top; its install notes ask launchers to leave
 *   the swipe down alone, and it shows the real status bar.
 * - It grants its two bundled launchers their access by package name. Home is not one of them.
 */
object IoriKompaktOS {
    private const val TAG = "IoriKompaktOS"
    const val SERVICE_PACKAGE = "com.kompakt.service"
    private const val ACTION_REFRESH = "com.kompakt.service.action.REFRESH"
    private const val KEY_DEFAULTS = "iori_kompaktos_defaults"

    @Volatile private var known: Boolean? = null

    /** Whether this phone runs KompaktOS: its Kompakt app is installed. */
    fun isKompaktOS(context: Context): Boolean = known ?: try {
        context.packageManager.getPackageInfo(SERVICE_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }.also { known = it }

    /** Asks KompaktOS for a full refresh of the panel. False where there is no KompaktOS. */
    fun refresh(context: Context): Boolean {
        if (!isKompaktOS(context)) return false
        return try {
            context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(SERVICE_PACKAGE))
            true
        } catch (e: Exception) {
            Log.w(TAG, "refresh broadcast failed", e)
            false
        }
    }

    /** Opens the Kompakt app, where the e-ink modes are. */
    fun openKompaktApp(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(SERVICE_PACKAGE) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not open the Kompakt app", e)
            false
        }
    }

    /**
     * Once per install, on KompaktOS: the real status bar on, and the swipe down left to the
     * system's shade when it still opens inkOS's own tray. Both stay changeable in Settings.
     */
    fun applyDefaultsOnce(context: Context, prefs: Prefs) {
        if (!isKompaktOS(context)) return
        val own = context.getSharedPreferences(Iori.PREFS, Context.MODE_PRIVATE)
        if (own.getBoolean(KEY_DEFAULTS, false)) return
        prefs.showStatusBar = true
        if (prefs.swipeDownAction == Constants.Action.OpenSimpleTray) {
            prefs.swipeDownAction = Constants.Action.Disabled
        }
        own.edit().putBoolean(KEY_DEFAULTS, true).apply()
    }

    /**
     * Whether a restored backup leaves this setting as KompaktOS has it. A backup made on
     * MuditaOS carries the status bar off and a swipe down to inkOS's tray, which would undo
     * [applyDefaultsOnce] on the move to KompaktOS. So there the status bar stays on, and a
     * swipe down to the tray is not restored; every other setting restores as saved.
     */
    fun keepOnRestore(context: Context, key: String, value: Any?): Boolean {
        if (!isKompaktOS(context)) return false
        return key == "STATUS_BAR" ||
            (key == "SWIPE_DOWN_ACTION" && value == Constants.Action.OpenSimpleTray.name)
    }

    /**
     * From Android 13 on, an app installed from a file is kept out of notification access and
     * Accessibility until App info, then ⋮, then Allow restricted settings. True on such a phone
     * for such an install; Android 12 (MuditaOS) has none of this. Whether it was already
     * allowed cannot be read, so callers ask only while the access is still missing.
     */
    fun restrictedBlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            val source = context.packageManager.getInstallSourceInfo(context.packageName).packageSource
            source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
                source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        } catch (_: Exception) {
            false
        }
    }

    fun appInfo(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:" + context.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
