package com.github.gezimos.inkos.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.github.gezimos.common.CrashHandler
import java.lang.ref.WeakReference

class ActionService : AccessibilityService() {

    private var lockscreenMusic: LockscreenMusic? = null

    override fun onServiceConnected() {
        instance = WeakReference(this)
        lockscreenMusic = LockscreenMusic(this).also { it.start() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = WeakReference(null)
        lockscreenMusic?.stop()
        lockscreenMusic = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        lockscreenMusic?.stop()
        lockscreenMusic = null
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun lockScreen(): Boolean {
        CrashHandler.logUserAction("Lock Screen")
        return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun showRecents(): Boolean {
        CrashHandler.logUserAction("Show Recents")
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun openQuickSettings(): Boolean {
        CrashHandler.logUserAction("Quick Settings")
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun openPowerDialog(): Boolean {
        CrashHandler.logUserAction("Power Dialog")
        return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }

    // Only the system UI's events arrive (see accessibility_service_config): the lock screen
    // showing, changing and going. Each is a cue to look again at whether the music controls
    // belong on it.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        lockscreenMusic?.evaluate()
    }
    override fun onInterrupt() {}

    companion object {
        private var instance: WeakReference<ActionService> = WeakReference(null)

        fun instance(): ActionService? {
            return instance.get()
        }
    }
}