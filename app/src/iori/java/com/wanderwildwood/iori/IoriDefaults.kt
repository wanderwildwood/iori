package com.wanderwildwood.iori

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.style.SettingsTheme
import com.github.gezimos.inkos.style.Theme
import com.github.gezimos.inkos.style.rememberScreenScale
import com.github.gezimos.inkos.style.scaled
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsHomeItem
import com.github.gezimos.inkos.ui.compose.inkOsSafeDrawingPadding
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.LocalListBleed
import com.wanderwildwood.iori.ui.PagedColumn
import com.wanderwildwood.iori.ui.houseTypography
import com.wanderwildwood.iori.ui.monochrome
import java.io.File

/**
 * Default apps: the things Android opens with "whichever app you chose", and which of Home's
 * apps is meant to take each. Some have no role behind them, only Android's own "open with"
 * question (alarms and timers, the gallery, a map link, a text file): where two apps can answer,
 * Android asks once and remembers "Always". Home cannot answer for the person. It lists each
 * one, says what Android would do today, and a press on one that is not chosen raises Android's
 * question, so it is asked now rather than in the middle of something else.
 *
 * Nothing here assumes a press worked: what Android would do is read again every time the page
 * comes back in front. Android also forgets an answer when the set of apps that can open the
 * thing changes, so a row may go back to "not chosen" after an app is installed.
 *
 * Phone and Texts are roles, which only the app itself may ask for; their rows show the state
 * and open Android's Default apps screen.
 */
object IoriDefaults {

    private const val OURS = "com.wanderwildwood."
    private const val ANDROID = "android" // the package of Android's own "open with" question

    /** What Android would do with a slot today. */
    sealed class State {
        object Ours : State()
        /**
         * Android would ask. [ask] raises the question; with none, [role] says whether it is a role
         * (Android's Default apps screen) or a thing Android only asks about when one is opened.
         */
        class NotChosen(val ask: Intent?, val role: Boolean = false) : State()
        class Other(val pkg: String, val name: String, val role: Boolean = false) : State()
    }

    /**
     * [askable] is false where raising the question would need a real thing to open (a picture, a
     * song, a PDF, a payment link): those rows only say what Android would do, and that it asks the
     * first time one is opened.
     */
    private class Slot(
        val label: Int,
        val pkg: String,
        val askable: Boolean = true,
        val role: ((Context) -> String?)? = null,
        val intents: (Context) -> List<Intent> = { emptyList() },
    )

    private fun main(category: String) = Intent(Intent.ACTION_MAIN).addCategory(category)
    private fun view(uri: String, type: String? = null) =
        Intent(Intent.ACTION_VIEW).apply { if (type == null) data = Uri.parse(uri) else setDataAndType(Uri.parse(uri), type) }

    // Every kind of thing one of Home's apps is meant to open. A row is shown only when that app is
    // on the phone AND is one of the apps Android would offer for it, so a slot an app does not
    // (or no longer does) answer for simply does not appear.
    private val SLOTS = listOf(
        Slot(R.string.iori_defaults_phone, OURS + "itodenwa", role = { it.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }),
        Slot(R.string.iori_defaults_texts, OURS + "kotozute", role = { Telephony.Sms.getDefaultSmsPackage(it) }),
        Slot(R.string.iori_defaults_contacts, OURS + "enishi") {
            listOf(main(Intent.CATEGORY_APP_CONTACTS), Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI))
        },
        Slot(R.string.iori_defaults_calendar, OURS + "koyomi") { listOf(main(Intent.CATEGORY_APP_CALENDAR)) },
        Slot(R.string.iori_defaults_email, OURS + "tayori") {
            listOf(main(Intent.CATEGORY_APP_EMAIL), Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
        },
        Slot(R.string.iori_defaults_alarms, OURS + "tokei") {
            listOf(Intent(AlarmClock.ACTION_SET_ALARM), Intent(AlarmClock.ACTION_SHOW_ALARMS), Intent(AlarmClock.ACTION_SET_TIMER))
        },
        Slot(R.string.iori_defaults_camera, OURS + "shashinki") {
            listOf(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        },
        Slot(R.string.iori_defaults_pictures, OURS + "garo") { listOf(main(Intent.CATEGORY_APP_GALLERY)) },
        Slot(R.string.iori_defaults_picture_files, OURS + "garo", askable = false) { listOf(view("content://media/external/images/media/0", "image/jpeg")) },
        Slot(R.string.iori_defaults_music, OURS + "jimeikin") { listOf(main(Intent.CATEGORY_APP_MUSIC)) },
        Slot(R.string.iori_defaults_music_files, OURS + "jimeikin", askable = false) { listOf(view("content://media/external/audio/media/0", "audio/mpeg")) },
        Slot(R.string.iori_defaults_files, OURS + "tana") { listOf(main(Intent.CATEGORY_APP_FILES)) },
        Slot(R.string.iori_defaults_text_files, OURS + "tana") { listOf(textFile(it)) },
        Slot(R.string.iori_defaults_pdf, OURS + "tana", askable = false) { listOf(view("content://media/external/file/0", "application/pdf")) },
        Slot(R.string.iori_defaults_maps, OURS + "chizu") { listOf(view("geo:0,0")) },
        Slot(R.string.iori_defaults_calculator, OURS + "soroban") { listOf(main(Intent.CATEGORY_APP_CALCULATOR)) },
        Slot(R.string.iori_defaults_magnet, OURS + "honryu", askable = false) { listOf(view("magnet:?xt=urn:btih:0000000000000000000000000000000000000000")) },
        Slot(R.string.iori_defaults_bitcoin, OURS + "zeni", askable = false) { listOf(view("bitcoin:")) },
    )

    /** A row of the page: the slot's label, the app of ours it belongs to, and Android's state. */
    class Line(val label: Int, val app: String, val state: State)

    /** A real small text file of Home's own, handed over readable, for the "open a text file" question. */
    private fun textFile(context: Context): Intent {
        val dir = File(context.filesDir, "iori_samples").apply { mkdirs() }
        val file = File(dir, "sample.txt")
        if (!file.exists()) runCatching { file.writeText(context.getString(R.string.iori_defaults_sample_text)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun label(pm: PackageManager, pkg: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** [resolved] against [ours]: whose it is, as [state] reads it. Kept apart so it can be tested. */
    internal fun whose(resolved: String?, ours: String): String = when {
        resolved == null -> "none"
        resolved == ours || resolved == "$ours.debug" -> "ours"
        resolved == ANDROID -> "ask"
        else -> "other"
    }

    /** The slots whose app is on the phone, each with what Android would do today. */
    fun lines(context: Context): List<Line> {
        val pm = context.packageManager
        return SLOTS.mapNotNull { slot ->
            val app = label(pm, slot.pkg) ?: return@mapNotNull null
            val state: State = if (slot.role != null) {
                val holder = runCatching { slot.role.invoke(context) }.getOrNull()
                when (whose(holder, slot.pkg)) {
                    "ours" -> State.Ours
                    "other" -> State.Other(holder!!, label(pm, holder) ?: holder, role = true)
                    else -> State.NotChosen(null, role = true)
                }
            } else {
                // Only the questions this app is one of the answers to.
                fun offered(intent: Intent) = runCatching {
                    pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).any { whose(it.activityInfo?.packageName, slot.pkg) == "ours" }
                }.getOrDefault(false)
                val answers = runCatching { slot.intents(context) }.getOrDefault(emptyList()).filter { offered(it) }.map { intent ->
                    intent to runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.getOrNull()
                }
                val undecided = answers.firstOrNull { whose(it.second, slot.pkg) == "ask" }
                val other = answers.firstOrNull { whose(it.second, slot.pkg) == "other" }
                when {
                    undecided != null -> State.NotChosen(undecided.first.takeIf { slot.askable })
                    other != null -> State.Other(other.second!!, label(pm, other.second!!) ?: other.second!!)
                    answers.any { whose(it.second, slot.pkg) == "ours" } -> State.Ours
                    else -> return@mapNotNull null // nothing on the phone answers it at all
                }
            }
            Line(slot.label, app, state)
        }
    }

    /** What a press on a row does: raise Android's question, or open the screen where it is changed. */
    private fun press(context: Context, line: Line) {
        val intent = when (val s = line.state) {
            is State.Ours -> return
            is State.NotChosen -> s.ask ?: if (s.role) Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) else return
            // A role is changed on Android's Default apps screen; an "Always" answer on the other app's own page.
            is State.Other -> if (s.role) Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) else
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${s.pkg}"))
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, R.string.iori_defaults_cannot_ask, Toast.LENGTH_LONG).show()
        }
    }

    /** The rows, read again each time the page is back in front. */
    @Composable
    private fun rememberLines(): List<Line> {
        val context = LocalContext.current
        var tick by remember { mutableIntStateOf(0) }
        OnResume { tick++ }
        return remember(tick) { lines(context) }
    }

    @Composable
    private fun Rows(lines: List<Line>, label: TextStyle, value: TextStyle, modifier: Modifier) {
        val context = LocalContext.current
        PagedColumn(modifier) {
            if (lines.isEmpty()) item(key = "none") { Text(stringResource(R.string.iori_defaults_none), style = value, modifier = Modifier.padding(vertical = 10.dp)) }
            lines.forEach { line ->
                item(key = "slot:${line.label}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = line.state.let { it is State.Other || (it is State.NotChosen && (it.ask != null || it.role)) }) { press(context, line) }
                            .padding(vertical = 9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(line.label), style = label, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Text(line.app, style = label, fontWeight = if (line.state is State.Ours) FontWeight.Bold else FontWeight.Normal)
                        }
                        val note = when (val s = line.state) {
                            is State.Ours -> null
                            is State.NotChosen -> stringResource(
                                when {
                                    s.ask != null -> R.string.iori_defaults_not_chosen
                                    s.role -> R.string.iori_defaults_not_chosen_role
                                    else -> R.string.iori_defaults_not_chosen_later
                                },
                                line.app,
                            )
                            is State.Other -> stringResource(R.string.iori_defaults_other, s.name)
                        }
                        if (note != null) Text(note, style = value)
                    }
                }
            }
        }
    }

    /** The Default apps page of a first run ([IoriFirstRun]), after Ecosystem, drawn as that page is. */
    @Composable
    fun Page(pageLabel: String, isDark: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        val sizes = remember { IoriFirstRun.sizes(context, prefs) }
        val lines = rememberLines()
        SettingsTheme(isDark = isDark) {
            val textColor = Theme.colors.text
            val bgColor = Theme.colors.background
            val screenScale = rememberScreenScale()
            BackHandler(enabled = true) { onBack() }
            Column(Modifier.fillMaxSize().background(bgColor).inkOsSafeDrawingPadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 32.dp.scaled(screenScale))
                        .padding(top = 32.dp.scaled(screenScale)),
                ) {
                    Text(
                        text = stringResource(R.string.iori_first_defaults),
                        style = SettingsTheme.typography.title,
                        fontSize = sizes.big,
                        fontWeight = FontWeight.Black,
                        color = textColor,
                        lineHeight = (sizes.big.value * 1.05f).sp,
                    )
                    Spacer(Modifier.height(16.dp.scaled(screenScale)))
                    Box(Modifier.width(60.dp.scaled(screenScale)).height(5.dp).background(textColor))
                    Spacer(Modifier.height(16.dp.scaled(screenScale)))
                    Text(
                        text = stringResource(R.string.iori_defaults_body),
                        style = SettingsTheme.typography.body,
                        fontSize = sizes.body,
                        color = textColor,
                        lineHeight = (sizes.body.value * 1.4f).sp,
                    )
                    Spacer(Modifier.height(8.dp.scaled(screenScale)))
                    val body = SettingsTheme.typography.body.copy(color = textColor)
                    MaterialTheme(colorScheme = monochrome.copy(primary = textColor, onPrimary = bgColor)) {
                        CompositionLocalProvider(LocalListBleed provides 26.dp.scaled(screenScale), LocalContentColor provides textColor) {
                            Rows(
                                lines = lines,
                                label = body.copy(fontSize = sizes.title),
                                value = body.copy(fontSize = sizes.body),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        }
                    }
                }
                with(IoriFirstRun) {
                    Footer(
                        left = stringResource(R.string.btn_back_upper),
                        middle = pageLabel,
                        right = stringResource(R.string.btn_next),
                        fontSize = sizes.title,
                        textColor = textColor,
                        onLeft = onBack,
                        onRight = onNext,
                    )
                }
            }
        }
    }

    /** The same rows from Settings, in one of Home's own dialogs. */
    @Composable
    fun SettingsRow(titleFontSize: TextUnit) {
        var open by remember { mutableStateOf(false) }
        SettingsHomeItem(
            title = stringResource(R.string.iori_defaults_title),
            imageVector = Icons.Rounded.Checklist,
            description = stringResource(R.string.iori_defaults_row_desc),
            titleFontSize = titleFontSize,
            onClick = { open = true },
        )
        if (!open) return
        val lines = rememberLines()
        MaterialTheme(colorScheme = monochrome, typography = houseTypography) {
            EInkDialog(onDismiss = { open = false }) {
                Text(stringResource(R.string.iori_defaults_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.iori_defaults_body), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Rows(lines, MaterialTheme.typography.bodyMedium, MaterialTheme.typography.bodySmall, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.iori_close),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Black)
                        .clickable { open = false }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}
