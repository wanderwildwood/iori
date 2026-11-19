package com.wanderwildwood.iori

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.helper.ShapeHelper
import com.github.gezimos.inkos.style.SettingsTheme
import com.github.gezimos.inkos.style.Theme
import com.github.gezimos.inkos.style.rememberScreenScale
import com.github.gezimos.inkos.style.scaled
import com.github.gezimos.inkos.ui.compose.inkOsSafeDrawingPadding
import com.wanderwildwood.iori.ui.LocalListBleed
import com.wanderwildwood.iori.ui.PagedColumn
import com.wanderwildwood.iori.ui.monochrome

/**
 * The Ecosystem page of a first run ([IoriFirstRun]): the apps Home is made to work with, under
 * their sections, the core set ticked, the rest not. The ticked ones are got one after the
 * other, through the same checked path as Your apps ([IoriAppInstaller]: the list on
 * wanderthe.dev, size, checksum, package, version and the pinned key); Android asks once for
 * each. Theme and home apps come after it, so what was got here can be put on the home screen
 * there.
 *
 * Nothing is asked of the network until the person presses GET.
 */
object IoriPickApps {

    @Composable
    fun Page(pageLabel: String, isDark: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        val apps = remember { IoriApps.apps(context) }
        val jobs by IoriAppInstaller.jobs.collectAsState()
        val queue by IoriAppInstaller.queue.collectAsState()
        // What is on the phone, read again when the page is back in front and when an install ends.
        var looked by remember { mutableStateOf(0) }
        OnResume {
            looked++
            IoriAppInstaller.resumed(context)
        }
        val ended = jobs.filterValues { it.step == IoriAppInstaller.Step.INSTALLED }.keys
        val onPhone = remember(apps, ended, looked) {
            apps.filter { IoriAppInstaller.installed(context, it.pkg) != null }.map { it.pkg }.toSet()
        }

        var ticked by remember { mutableStateOf(IoriApps.firstPick(apps, onPhone)) }
        var total by remember { mutableStateOf(0) }
        var askedToAllow by remember { mutableStateOf(false) }

        // Ticked and still missing: what GET would fetch. A failed one stays here for another try.
        val wanted = apps.filter { it.pkg in ticked && it.pkg !in onPhone }
        val running = queue.isNotEmpty()

        // Leaving the page lets the app in hand through and drops the ones waiting behind it.
        fun leave(then: () -> Unit) {
            IoriAppInstaller.stopQueue()
            then()
        }

        val sizes = remember { IoriFirstRun.sizes(context, prefs) }

        SettingsTheme(isDark = isDark) {
            val textColor = Theme.colors.text
            val bgColor = Theme.colors.background
            val screenScale = rememberScreenScale()

            BackHandler(enabled = true) { leave(onBack) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
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
                        text = stringResource(R.string.iori_first_ecosystem),
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
                        text = stringResource(
                            when {
                                askedToAllow && !running && !IoriAppInstaller.allowed(context) -> R.string.iori_pick_allow
                                else -> R.string.iori_pick_body
                            }
                        ),
                        style = SettingsTheme.typography.body,
                        fontSize = sizes.body,
                        color = textColor,
                        lineHeight = (sizes.body.value * 1.4f).sp,
                    )
                    Spacer(Modifier.height(8.dp.scaled(screenScale)))
                    // The house scrollbar, in this page's own two colors, run out to the screen's edge.
                    MaterialTheme(colorScheme = monochrome.copy(primary = textColor, onPrimary = bgColor)) {
                        // (the arrows of the bar are drawn in the content color, so that is this page's ink too)
                        CompositionLocalProvider(LocalListBleed provides 26.dp.scaled(screenScale), LocalContentColor provides textColor) {
                            PagedColumn(Modifier.fillMaxWidth().weight(1f)) {
                                IoriApps.sections(apps).forEach { (head, inSection) ->
                                    item(key = "head:$head") {
                                        Text(
                                            text = stringResource(head),
                                            style = SettingsTheme.typography.title,
                                            fontSize = sizes.title,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                        )
                                    }
                                    inSection.forEach { app ->
                                        item(key = app.pkg) {
                                            PickRow(
                                                app = app,
                                                onPhone = app.pkg in onPhone,
                                                job = jobs[app.pkg],
                                                ticked = app.pkg in ticked,
                                                waiting = app.pkg in queue,
                                                fontSize = sizes.body,
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
                    }
                }

                // GET is a button of its own, at the left under the list, never in the place of
                // NEXT: the bar stays BACK and NEXT, so a press meant for NEXT (or one press too
                // many from the page before) cannot start a download and Android's install screen.
                val canGet = !running && wanted.isNotEmpty()
                if (canGet) {
                    val shape = remember(prefs.textIslandsShape) {
                        ShapeHelper.getRoundedCornerShape(textIslandsShape = prefs.textIslandsShape, pillRadius = 16.dp)
                    }
                    Text(
                        text = pluralStringResource(R.plurals.iori_pick_get_count, wanted.size, wanted.size),
                        style = SettingsTheme.typography.title,
                        fontSize = sizes.title,
                        color = textColor,
                        modifier = Modifier
                            .padding(horizontal = 32.dp.scaled(screenScale), vertical = 12.dp.scaled(screenScale))
                            .clip(shape)
                            .border(1.5.dp.scaled(screenScale), textColor, shape)
                            .clickable {
                                // Android's own switch first; nothing is downloaded without it.
                                if (!IoriAppInstaller.allowed(context)) {
                                    askedToAllow = true
                                    IoriAppInstaller.askToAllow(context)
                                } else {
                                    total = wanted.size
                                    IoriAppInstaller.installAll(context, wanted.map { it.pkg })
                                }
                            }
                            .padding(horizontal = 24.dp.scaled(screenScale), vertical = 10.dp.scaled(screenScale)),
                    )
                }
                with(IoriFirstRun) {
                    Footer(
                        left = stringResource(R.string.btn_back_upper),
                        // While apps are being got, how far along; else the page.
                        middle = if (running) "${total - queue.size + 1}/$total" else pageLabel,
                        right = stringResource(R.string.btn_next),
                        fontSize = sizes.title,
                        textColor = textColor,
                        onLeft = { leave(onBack) },
                        // NEXT goes on without getting what is ticked; one being got is let through.
                        onRight = { leave(onNext) },
                    )
                }
            }
        }
    }

    @Composable
    private fun PickRow(
        app: IoriApps.App,
        onPhone: Boolean,
        job: IoriAppInstaller.Job?,
        ticked: Boolean,
        waiting: Boolean,
        fontSize: TextUnit,
        textColor: Color,
        onToggle: () -> Unit,
    ) {
        val status = when {
            onPhone -> stringResource(R.string.iori_pick_on_phone)
            else -> shortStatus(job) ?: if (waiting) stringResource(R.string.iori_pick_waiting) else ""
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
