package com.wanderwildwood.iori

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.style.SettingsTheme
import com.github.gezimos.inkos.style.Theme
import com.github.gezimos.inkos.style.rememberScreenScale
import com.github.gezimos.inkos.style.scaled
import com.github.gezimos.inkos.ui.compose.inkOsSafeDrawingPadding
import com.wanderwildwood.iori.ui.PagedColumn

/**
 * The last page of a first run: the apps Home is made to work with, the core set ticked, the
 * extras not. The ticked ones are got one after the other; Android asks once for each. The
 * page is drawn like inkOS's own first-run pages and comes up when their FINISH is pressed.
 *
 * Nothing is asked of the network until the person presses GET.
 */
object IoriPickApps {

    private var showing by mutableStateOf(false)
    private var finish: () -> Unit = {}

    /**
     * Wraps the first run's finish: this page comes first when any of the apps is missing,
     * and its own FINISH then runs [finish].
     */
    fun before(context: Context, finish: () -> Unit): () -> Unit = {
        val app = context.applicationContext
        IoriApps.refreshInstalled(app)
        val states = IoriApps.states.value
        if (IoriApps.apps(app).any { states[it.pkg]?.installed == null }) {
            this.finish = finish
            showing = true
        } else {
            finish()
        }
    }

    /** Drawn over the first-run pages while it is up; nothing otherwise. */
    @Composable
    fun Page() {
        if (!showing) return
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        val apps = remember { IoriApps.apps(context) }
        val states by IoriApps.states.collectAsState()
        val queue by IoriApps.queue.collectAsState()
        LaunchedEffect(Unit) { IoriApps.refreshInstalled(context) }
        OnResume { IoriApps.resumed(context) }

        var ticked by remember {
            mutableStateOf(
                IoriApps.firstPick(apps, apps.filter { IoriApps.states.value[it.pkg]?.installed != null }.map { it.pkg }.toSet())
            )
        }
        var total by remember { mutableStateOf(0) }
        var askedToAllow by remember { mutableStateOf(false) }

        // Ticked and still missing: what GET would fetch. A failed one stays here for another try.
        val wanted = apps.filter { it.pkg in ticked && states[it.pkg]?.installed == null }
        val running = queue.isNotEmpty()

        fun leave() {
            IoriApps.stopQueue()
            showing = false
        }

        // The same sizes inkOS's first-run pages work out for themselves.
        val scaleRaw = com.github.gezimos.inkos.style.detectScaleMode(context).let { mode ->
            if (prefs.uiScaleMode != 0) com.github.gezimos.inkos.style.UiScaleMode.fromId(prefs.uiScaleMode).scale
            else mode.scale
        }
        val settingsSize = prefs.settingsSize - 3
        val titleFontSize = (settingsSize * 1.5f * scaleRaw).sp
        val bodyFontSize = (settingsSize * 1.2f * scaleRaw).sp
        val bigTitleSize = (settingsSize * 3f * scaleRaw).sp
        val isDark = when (prefs.appTheme) {
            Constants.Theme.Dark -> true
            Constants.Theme.Light -> false
            Constants.Theme.System -> isSystemInDarkTheme()
        }

        SettingsTheme(isDark = isDark) {
            val textColor = Theme.colors.text
            val bgColor = Theme.colors.background
            val screenScale = rememberScreenScale()

            BackHandler(enabled = true) { leave() }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    // A press on an empty part of this page stops here, not on the page under it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .inkOsSafeDrawingPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 32.dp.scaled(screenScale))
                        .padding(top = 32.dp.scaled(screenScale))
                ) {
                    Text(
                        text = stringResource(R.string.iori_apps_title),
                        style = SettingsTheme.typography.title,
                        fontSize = bigTitleSize,
                        fontWeight = FontWeight.Black,
                        color = textColor,
                        lineHeight = (bigTitleSize.value * 1.05f).sp,
                    )
                    Spacer(Modifier.height(16.dp.scaled(screenScale)))
                    Box(Modifier.width(60.dp.scaled(screenScale)).height(5.dp).background(textColor))
                    Spacer(Modifier.height(16.dp.scaled(screenScale)))
                    Text(
                        text = stringResource(
                            when {
                                askedToAllow && !running && !IoriApps.allowed(context) -> R.string.iori_pick_allow
                                else -> R.string.iori_pick_body
                            }
                        ),
                        style = SettingsTheme.typography.body,
                        fontSize = bodyFontSize,
                        color = textColor,
                        lineHeight = (bodyFontSize.value * 1.4f).sp,
                    )
                    Spacer(Modifier.height(8.dp.scaled(screenScale)))
                    PagedColumn(Modifier.fillMaxWidth().weight(1f)) {
                        listOf(true to R.string.iori_apps_core, false to R.string.iori_apps_extras).forEach { (core, head) ->
                            item(key = "head:$core") {
                                Text(
                                    text = stringResource(head),
                                    style = SettingsTheme.typography.title,
                                    fontSize = titleFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                            apps.filter { it.core == core }.forEach { app ->
                                item(key = app.pkg) {
                                    PickRow(
                                        app = app,
                                        state = states[app.pkg] ?: IoriApps.State(),
                                        ticked = app.pkg in ticked,
                                        waiting = app.pkg in queue,
                                        fontSize = bodyFontSize,
                                        textColor = textColor,
                                        onToggle = {
                                            if (!running) ticked = if (app.pkg in ticked) ticked - app.pkg else ticked + app.pkg
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.5.dp.scaled(screenScale)).background(textColor))
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp.scaled(screenScale)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FooterButton(stringResource(R.string.btn_back_upper), titleFontSize, textColor, Modifier.weight(1f)) { leave() }
                    Box(Modifier.width(1.5.dp.scaled(screenScale)).fillMaxHeight().background(textColor))
                    Box(Modifier.width(54.dp.scaled(screenScale)), contentAlignment = Alignment.Center) {
                        Text(
                            // While apps are being got, how far along; before that, how many are ticked.
                            text = if (running) "${total - queue.size + 1}/$total" else wanted.size.toString(),
                            style = SettingsTheme.typography.body,
                            fontSize = (titleFontSize.value * 0.7f).sp,
                            color = textColor,
                        )
                    }
                    Box(Modifier.width(1.5.dp.scaled(screenScale)).fillMaxHeight().background(textColor))
                    FooterButton(
                        text = stringResource(if (!running && wanted.isNotEmpty()) R.string.iori_pick_get else R.string.btn_finish),
                        fontSize = titleFontSize,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    ) {
                        when {
                            // FINISH: what is being got is let through, the rest wait in Settings.
                            running || wanted.isEmpty() -> {
                                leave()
                                finish()
                            }
                            // Android's own switch first; nothing is downloaded without it.
                            !IoriApps.allowed(context) -> {
                                askedToAllow = true
                                IoriApps.askToAllow(context)
                            }
                            else -> {
                                total = wanted.size
                                IoriApps.installAll(context, wanted)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FooterButton(text: String, fontSize: TextUnit, color: Color, modifier: Modifier, onClick: () -> Unit) {
        Box(
            modifier = modifier.fillMaxHeight().clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = SettingsTheme.typography.title,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }

    @Composable
    private fun PickRow(
        app: IoriApps.App,
        state: IoriApps.State,
        ticked: Boolean,
        waiting: Boolean,
        fontSize: TextUnit,
        textColor: Color,
        onToggle: () -> Unit,
    ) {
        val onPhone = state.installed != null
        val status = when {
            onPhone -> stringResource(R.string.iori_pick_on_phone)
            else -> progressText(state) ?: if (waiting) stringResource(R.string.iori_pick_waiting) else ""
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !onPhone, onClick = onToggle)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A tick box: an outline, filled when ticked. An app already on the phone has none.
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .then(if (onPhone) Modifier else Modifier.border(1.5.dp, textColor))
                    .padding(4.dp)
                    .then(if (ticked && !onPhone) Modifier.background(textColor) else Modifier)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = app.name,
                style = SettingsTheme.typography.body,
                fontSize = fontSize,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (status.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = status,
                    style = SettingsTheme.typography.body,
                    fontSize = (fontSize.value * 0.85f).sp,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}
