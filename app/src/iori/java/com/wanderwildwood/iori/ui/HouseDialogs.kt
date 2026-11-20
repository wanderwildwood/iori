package com.wanderwildwood.iori.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/**
 * Home's dialogs ([EInkDialog]) that are up, so the screen is never recreated under one.
 *
 * Home recreates its activity when the dark setting changes ([com.wanderwildwood.iori.IoriDark]).
 * Recreated with a dialog's window still up (Your apps with a folder open, Home lines, About),
 * the dialog was gone from the new screen but Android's accessibility still held its window:
 * TalkBack and every accessibility service read nothing from Home until something was pressed
 * (emulator, 19 Nov 2026). So the dialogs are closed first, and the activity is recreated once
 * their windows are gone.
 */
object HouseDialogs {

    /** How many of Home's dialogs are up. */
    internal var open by mutableIntStateOf(0)

    /** The activity whose dialogs are being closed before it is recreated. */
    private var closingFor by mutableStateOf<WeakReference<Activity>?>(null)

    /** True while [context]'s activity is waiting for its dialogs to close: none is drawn on it. */
    fun closing(context: Context): Boolean {
        val activity = context.activity() ?: return false
        return closingFor?.get() === activity
    }

    /** Recreates [activity], at once when no dialog of Home's is up, else as soon as they are closed. */
    fun recreate(activity: Activity) {
        if (open == 0) return activity.recreate()
        closingFor = WeakReference(activity)
        val view = activity.window.decorView
        var waited = 0
        fun check() {
            if (activity.isFinishing || activity.isDestroyed) return
            // A dialog that does not go within a second is not waited for any longer.
            if (open == 0 || ++waited > 20) activity.recreate() else view.postDelayed({ check() }, 50)
        }
        view.post { check() }
    }

    private tailrec fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
}
