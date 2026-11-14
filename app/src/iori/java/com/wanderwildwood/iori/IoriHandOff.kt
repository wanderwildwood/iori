package com.wanderwildwood.iori

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.R
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.monochrome

/**
 * Where Home hands over to one of its own apps: the clock to Time, the date to Datebook, a
 * person to Connections. This is for when no app has been picked for the clock or the date.
 *
 * The app is opened when it is on the phone. When it is not, Home says so once and offers to
 * get it, as Your apps would; "Use the phone's own" is remembered, and from then on the press
 * does what inkOS does without asking.
 */
object IoriHandOff {

    const val TIME = "com.wanderwildwood.tokei"
    const val DATEBOOK = "com.wanderwildwood.koyomi"

    private fun declinedKey(pkg: String) = "handoff_declined_$pkg"

    private fun installed(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** True when the press was taken here; false for inkOS to do what it always did. */
    fun clock(context: Context, phonesOwn: () -> Unit): Boolean = handOver(context, TIME, null, phonesOwn)

    /** The date opens Datebook on today, through the same intent inkOS sends to any calendar. */
    fun date(context: Context, phonesOwn: () -> Unit): Boolean {
        val today = Intent(
            Intent.ACTION_VIEW,
            android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                .appendPath(System.currentTimeMillis().toString()).build(),
        ).setPackage(DATEBOOK)
        return handOver(context, DATEBOOK, today, phonesOwn)
    }

    /**
     * Opens [pkg] (with [intent] if it takes it, as its launcher would otherwise), or offers to
     * get it. False when the app is missing and the person has said to use the phone's own, or
     * when there is nothing to show the offer on.
     */
    fun handOver(context: Context, pkg: String, intent: Intent?, phonesOwn: () -> Unit): Boolean {
        if (installed(context, pkg)) return open(context, pkg, intent)
        val app = IoriApps.find(context, pkg) ?: return false
        if (Iori.prefs(context).getBoolean(declinedKey(pkg), false)) return false
        val activity = context.activity() ?: return false
        offer(activity, app, intent, phonesOwn)
        return true
    }

    private fun open(context: Context, pkg: String, intent: Intent?): Boolean {
        val pm = context.packageManager
        val start = intent?.takeIf { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null }
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return false
        return runCatching { context.startActivity(start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private tailrec fun Context.activity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }

    /** Puts the offer over the screen the press was made on, and takes it away when answered. */
    private fun offer(activity: Activity, app: IoriApps.App, intent: Intent?, phonesOwn: () -> Unit) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val host = ComposeView(activity)
        fun close() = root.post { root.removeView(host) }
        host.setContent {
            Offer(
                app = app,
                onGot = {
                    close()
                    open(activity, app.pkg, intent)
                },
                onPhonesOwn = {
                    Iori.prefs(activity).edit().putBoolean(declinedKey(app.pkg), true).apply()
                    close()
                    phonesOwn()
                },
                onDismiss = { close() },
            )
        }
        root.addView(host, ViewGroup.LayoutParams(0, 0))
    }

    @Composable
    private fun Offer(app: IoriApps.App, onGot: () -> Unit, onPhonesOwn: () -> Unit, onDismiss: () -> Unit) {
        val context = LocalContext.current
        val states by IoriApps.states.collectAsState()
        val state = states[app.pkg] ?: IoriApps.State()
        LaunchedEffect(Unit) { IoriApps.refreshInstalled(context) }
        OnResume { IoriApps.resumed(context) }
        // Once it is on the phone, the press that started this is carried out.
        LaunchedEffect(state.installed) { if (state.installed != null) onGot() }

        MaterialTheme(colorScheme = monochrome) {
            EInkDialog(onDismiss = onDismiss) {
                Text(
                    text = stringResource(R.string.iori_handoff_missing, app.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Press(progressText(state) ?: stringResource(R.string.iori_handoff_get, app.name)) {
                    IoriApps.install(context, app)
                }
                Spacer(Modifier.height(10.dp))
                Press(stringResource(R.string.iori_handoff_phones_own), onPhonesOwn)
                Spacer(Modifier.height(10.dp))
                Press(stringResource(R.string.iori_close), onDismiss)
            }
        }
    }

    @Composable
    private fun Press(text: String, onClick: () -> Unit) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Black)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        )
    }
}
