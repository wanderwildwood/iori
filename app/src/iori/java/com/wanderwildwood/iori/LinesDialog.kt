package com.wanderwildwood.iori

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.BuildConfig
import com.github.gezimos.inkos.R
import com.wanderwildwood.iori.IoriLinesRepository.GlanceState
import com.wanderwildwood.iori.IoriLinesRepository.Status
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.PagedColumn
import com.wanderwildwood.iori.ui.houseTypography
import com.wanderwildwood.iori.ui.monochrome

/**
 * Every source the lines come from, and what each is doing: on, off here, not installed, needs
 * an update, off in its own app, off in Glance, or did not answer. When the trouble is Glance
 * itself it says that instead. Pressing a source switches it on or off on home only; pressing
 * one that is not installed or needs an update gets it, and so for Glance, as Your apps would.
 */
@Composable
fun LinesDialog(tripped: Boolean, onTurnBackOn: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val snapshot by IoriLinesRepository.snapshot.collectAsState()
    // Bumped on each press, so a switch reads back at once while the new answer is on its way.
    var presses by remember { mutableIntStateOf(0) }
    // Asked afresh on opening, even while lines are turned off after a crash: reading the
    // sources' status is the guarded part, and the dialog is where the reader looks for why.
    LaunchedEffect(Unit) {
        IoriLinesRepository.refresh(context)
        IoriApps.refreshInstalled(context)
    }
    OnResume { IoriApps.resumed(context) }
    val prefs = remember { Iori.prefs(context) }
    val installs by IoriApps.states.collectAsState()
    // Asked again once an app got from here is on the phone.
    val onPhone = installs.count { it.value.installed != null }
    LaunchedEffect(onPhone) { IoriLinesRepository.refresh(context) }

    // Each row on its own, so a long list or a large text size pages by whole rows.
    val rows = ArrayList<Pair<String, @Composable () -> Unit>>()
    if (tripped) {
        rows.row("tripped") {
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onTurnBackOn).padding(top = 6.dp, bottom = 12.dp)) {
                Text(stringResource(R.string.iori_lines_tripped_dialog), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    // Where the lines are drawn on home; a press moves them.
    rows.row("place") {
        val top = IoriLinesPlace.top(context)
        Row(
            modifier = Modifier.fillMaxWidth().clickable { IoriLinesPlace.set(context, !top) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.iori_lines_place), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (top) R.string.iori_lines_place_top else R.string.iori_lines_place_bottom),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    when (snapshot.glance) {
        GlanceState.NOT_INSTALLED -> rows.row("glance") {
            GetLine(stringResource(R.string.iori_glance_not_installed), IoriLinesRepository.GLANCE, installs)
        }
        GlanceState.NEEDS_UPDATE -> rows.row("glance") {
            GetLine(stringResource(R.string.iori_glance_needs, IoriLinesRepository.GLANCE_NEEDED), IoriLinesRepository.GLANCE, installs)
        }
        GlanceState.REFUSED -> rows.row("glance") { Line(stringResource(R.string.iori_glance_refused)) }
        GlanceState.NO_ANSWER -> rows.row("glance") { Line(stringResource(R.string.iori_glance_no_answer)) }
        GlanceState.UNKNOWN -> rows.row("glance") { Line(stringResource(R.string.iori_glance_unknown)) }
        GlanceState.OK -> {
            if (snapshot.sources.isEmpty()) rows.row("none") { Line(stringResource(R.string.iori_lines_none)) }
            snapshot.sources.forEach { source ->
                rows.row("source:${source.pkg}") { SourceRow(source, presses, installs[source.pkg]) { presses++ } }
            }
        }
    }
    if (BuildConfig.DEBUG) {
        rows.row("crash-own") {
            DebugPress(stringResource(R.string.iori_debug_crash_own), Modifier.padding(top = 8.dp)) {
                prefs.edit().putString(Iori.KEY_HOME_FAULT, HomeFault.OWN).commit()
                onDismiss()
            }
        }
        rows.row("crash-upstream") {
            DebugPress(stringResource(R.string.iori_debug_crash_upstream)) {
                prefs.edit().putString(Iori.KEY_HOME_FAULT, HomeFault.UPSTREAM).commit()
                onDismiss()
            }
        }
    }

    MaterialTheme(colorScheme = monochrome, typography = houseTypography) {
        EInkDialog(onDismiss = onDismiss) {
            Text(
                text = stringResource(R.string.iori_lines_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            // Takes what the title and Close leave, so Close is always on screen.
            FittedPages(rows, Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.iori_close),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Black)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/**
 * [rows] in a [PagedColumn] only as tall as they are, up to the room it is given. A paged list
 * fills whatever height it is handed, so the rows are measured once on their own first, never
 * drawn, to know how much of the room a short list needs.
 */
@Composable
private fun FittedPages(rows: List<Pair<String, @Composable () -> Unit>>, modifier: Modifier) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val natural = subcompose("measure") { rows.forEach { (_, row) -> row() } }
            .sumOf { it.measure(loose).height }
        val height = natural.coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = constraints.maxWidth
        val list = subcompose("rows") {
            PagedColumn(Modifier.fillMaxWidth()) {
                rows.forEach { (key, row) -> item(key = key) { row() } }
            }
        }.map { it.measure(Constraints.fixed(width, height)) }
        layout(width, height) { list.forEach { it.place(0, 0) } }
    }
}

private fun MutableList<Pair<String, @Composable () -> Unit>>.row(key: String, content: @Composable () -> Unit) {
    add(key to content)
}

/** One source, what it is doing, and in a debug build the fault it is made to give. */
@Composable
private fun SourceRow(source: IoriLinesRepository.Source, presses: Int, install: IoriApps.State?, onPressed: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Iori.prefs(context) }
    val onHere = remember(presses, source.pkg) { prefs.getBoolean(Iori.lineKey(source.pkg), true) }
    val status = when {
        !onHere -> Status.OFF_HERE
        source.status == Status.OFF_HERE -> Status.ON
        else -> source.status
    }
    val fault = remember(presses, source.pkg) {
        if (BuildConfig.DEBUG) prefs.getString(Iori.faultKey(source.pkg), null) else null
    }
    // A source that is missing or too old, and is one of the apps Home can get: a press gets it.
    val gettable = remember(source.pkg) { IoriApps.find(context, source.pkg) }
        .takeIf { status == Status.NOT_INSTALLED || status == Status.NEEDS_UPDATE }
    val progress = if (gettable != null) progressText(install ?: IoriApps.State()) else null
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val statusRoom = maxWidth * 0.6f
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (gettable != null) {
                            IoriApps.install(context, gettable)
                        } else {
                            prefs.edit().putBoolean(Iori.lineKey(source.pkg), !onHere).apply()
                            onPressed()
                            IoriLinesRepository.refresh(context)
                        }
                    }
                    .padding(vertical = 5.dp),
            ) {
                Text(
                    text = source.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (status == Status.ON) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = (progress ?: statusText(status, source.label, gettable != null)) + (fault?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = statusRoom),
                )
            }
        }
        if (BuildConfig.DEBUG) {
            DebugPress(stringResource(R.string.iori_debug_fault_source), Modifier.padding(bottom = 4.dp)) {
                val next = Fault.CYCLE[(Fault.CYCLE.indexOf(fault) + 1) % Fault.CYCLE.size]
                prefs.edit().putString(Iori.faultKey(source.pkg), next).apply()
                onPressed()
                IoriLinesRepository.refresh(context)
            }
        }
    }
}

/** Debug builds only: a small press for the fault switches. */
@Composable
private fun DebugPress(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

@Composable
private fun Line(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 5.dp))
}

/** Glance's own trouble, with Get beside it when Glance is one of the apps Home can get. */
@Composable
private fun GetLine(text: String, pkg: String, installs: Map<String, IoriApps.State>) {
    val context = LocalContext.current
    val app = remember(pkg) { IoriApps.find(context, pkg) }
    if (app == null) {
        Line(text)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { IoriApps.install(context, app) }
            .padding(vertical = 5.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            text = progressText(installs[pkg] ?: IoriApps.State()) ?: stringResource(R.string.iori_apps_get),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun statusText(status: Status, label: String, gettable: Boolean = false): String = when (status) {
    Status.ON -> stringResource(R.string.iori_status_on)
    Status.OFF_HERE -> stringResource(R.string.iori_status_off_here)
    Status.NOT_INSTALLED -> stringResource(if (gettable) R.string.iori_status_not_installed_get else R.string.iori_status_not_installed)
    Status.NEEDS_UPDATE -> stringResource(if (gettable) R.string.iori_status_needs_update_get else R.string.iori_status_needs_update)
    Status.OFF_APP -> stringResource(R.string.iori_status_off_app, label)
    Status.OFF_GLANCE -> stringResource(R.string.iori_status_off_glance)
    Status.NO_ANSWER -> stringResource(R.string.iori_status_no_answer)
}
