package com.wanderwildwood.iori

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.helper.device.DeviceHelper

/**
 * An app's own e-ink mode, from its long-press menu in the app drawer (MuditaOS 2.0, decided
 * 11-19). MuditaOS 2.0 keeps one mode per app in its meink service, the same table its own
 * E-ink Modes screen reads and writes. A mode set here stays with the app until it is set
 * back to the phone's default, here or there.
 *
 * Only on MuditaOS: KompaktOS has no meink service (its modes live in its Kompakt app) and plain
 * Android has none, and MuditaOS before 2.0 has no per-app calls. Wherever the read fails, the
 * menu row is simply not there.
 */
object IoriEinkModes {
    private const val TAG = "IoriEinkModes"
    private const val SERVICE = "meink"
    private const val DESCRIPTOR = "android.meink.IMeinkService"

    // IMeinkService in MuditaOS 2.0 (read from its framework, 11-19).
    private const val UPDATE_FOR_APP = 6 // updateDisplayModeForAppPackage(String, int)
    private const val GET_FOR_APP = 7 // getDisplayModeForAppPackage(String): int

    /** No mode of its own: the app takes the phone's default. */
    const val PHONE_DEFAULT = -1

    /**
     * The modes, as MuditaEinkParameters.RefreshMode numbers them (its ordinals: AUTO 0, FAST 1,
     * FAST_DITHER 2, TEXT 3, QUALITY 4), each with its name in the list.
     */
    val MODES: List<Pair<Int, Int>> = listOf(
        PHONE_DEFAULT to R.string.iori_eink_phone_default,
        0 to R.string.iori_eink_auto,
        1 to R.string.iori_eink_fast,
        2 to R.string.iori_eink_fast_dither,
        3 to R.string.iori_eink_text,
        4 to R.string.iori_eink_quality,
    )

    @SuppressLint("PrivateApi")
    private fun service(): IBinder? {
        if (!DeviceHelper.isMuditaKompakt()) return null
        return try {
            Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String::class.java)
                .invoke(null, SERVICE) as? IBinder
        } catch (e: Exception) {
            null
        }
    }

    /** [pkg]'s own mode ([PHONE_DEFAULT] when it has none), or null where there are no per-app modes. */
    fun get(pkg: String): Int? {
        val binder = service() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(pkg)
            if (!binder.transact(GET_FOR_APP, data, reply, 0)) return null
            reply.readException()
            reply.readInt()
        } catch (e: Exception) {
            Log.w(TAG, "no per-app e-ink modes here", e)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Gives [pkg] its own [mode], or [PHONE_DEFAULT] to take its own away. True when it took. */
    fun set(pkg: String, mode: Int): Boolean {
        val binder = service() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(pkg)
            data.writeInt(mode)
            if (!binder.transact(UPDATE_FOR_APP, data, reply, 0)) return false
            reply.readException()
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not set the e-ink mode", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Whether the menu offers the row: per-app modes exist on this phone. */
    fun offered(pkg: String): Boolean = pkg.isNotEmpty() && get(pkg) != null

    /** The list of modes for [pkg], its own one ticked; a pick is written at once. */
    fun choose(context: Context, pkg: String, title: String) {
        val activity = context.activity() ?: return
        val current = get(pkg) ?: return
        val dialogs = IoriDialogs(activity) {}
        dialogs.choose(
            title = title,
            labels = MODES.map { activity.getString(it.second) },
            selected = MODES.indexOfFirst { it.first == current }.takeIf { it >= 0 },
        ) { i -> set(pkg, MODES[i].first) }
    }

    private fun Context.activity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
