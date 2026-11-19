package com.wanderwildwood.iori

import android.content.Context
import android.content.SharedPreferences

/**
 * iori's own state, and the guard that keeps a crash in iori's code from costing the home screen.
 *
 * Everything iori keeps lives in its own preferences file, [PREFS], outside inkOS's backup and
 * theme export, and holds only Boolean, Int and String values: inkOS's import reads numbers back
 * as doubles, and a Long would be the one type that could not survive a round trip if this file
 * were ever folded into it.
 */
object Iori {

    const val PREFS = "iori"

    /** Set by [Guard] when a crash came from iori's own code; home lines stay off until cleared. */
    const val KEY_TRIPPED = "iori_tripped"

    /** The per-source switch on home, `line_<package>`, on unless switched off here. */
    fun lineKey(pkg: String) = "line_$pkg"

    /** Debug builds only: what a source is made to do instead of answering. See [Fault]. */
    fun faultKey(pkg: String) = "fault_$pkg"

    /** Debug builds only: a fault thrown from home itself, to test [Guard]. See [HomeFault]. */
    const val KEY_HOME_FAULT = "fault_home"

    /** Classes of iori's own code, as they appear in a stack trace. Kept unrenamed by R8. */
    private const val OWN_CODE = "com.wanderwildwood.iori."

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun tripped(context: Context): Boolean = try {
        prefs(context).getBoolean(KEY_TRIPPED, false)
    } catch (_: Exception) {
        false
    }

    fun clearTripped(context: Context) {
        try {
            prefs(context).edit().putBoolean(KEY_TRIPPED, false).apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Wraps whatever uncaught-exception handler is current, once.
     *
     * Called right after MainActivity's onCreate puts inkOS's CrashHandler back as the default
     * handler, which it does on every create, so the guard has to follow that line rather than
     * be installed once from the Application. If the handler is already a [Guard] it is left
     * alone, so a recreate never stacks two.
     */
    fun guardCrashes(context: Context) {
        IoriLinesRepository.warm(context)
        // Also the first thing of iori's at each start: settings that name what was removed.
        IoriRemoved.migrate(context)
        try {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is Guard) return
            Thread.setDefaultUncaughtExceptionHandler(Guard(context.applicationContext, current))
        } catch (_: Exception) {
        }
    }

    /** Whether [error], or anything that caused it, was thrown from iori's own code. */
    fun fromOwnCode(error: Throwable): Boolean {
        var e: Throwable? = error
        var depth = 0
        while (e != null && depth < 16) {
            if (e.stackTrace.any { it.className.startsWith(OWN_CODE) }) return true
            e = e.cause
            depth++
        }
        return false
    }

    /**
     * Notes a crash from iori's code, synchronously because the process is about to die, then
     * hands the crash on unchanged. inkOS's CrashHandler still shows its report every time;
     * what changes is the next start, where home draws with its lines turned off, and a first
     * run starts past the page it crashed on ([IoriFirstRun.tripped]).
     */
    class Guard(
        private val context: Context,
        private val next: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            try {
                if (fromOwnCode(error)) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_TRIPPED, true).commit()
                    // During a first run, the next start passes by the page that was up.
                    IoriFirstRun.tripped(context)
                }
            } catch (_: Throwable) {
            }
            if (next != null) {
                next.uncaughtException(thread, error)
            } else {
                // Nothing to hand on to: end the process as the platform would, rather than
                // leave a launcher running with a dead thread.
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
