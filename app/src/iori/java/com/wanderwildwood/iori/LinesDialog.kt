package com.wanderwildwood.iori

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.BuildConfig
import com.github.gezimos.inkos.R
import com.wanderwildwood.iori.IoriLinesRepository.GlanceState
import com.wanderwildwood.iori.IoriLinesRepository.Status
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.monochrome

/**
 * Every source the lines come from, and what each is doing: on, off here, not installed, needs
 * an update, off in its own app, off in Glance, or did not answer. When the trouble is Glance
 * itself it says that instead. Pressing a source switches it on or off on home only.
 */
@Composable
fun LinesDialog(tripped: Boolean, onTurnBackOn: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val snapshot by IoriLinesRepository.snapshot.collectAsState()
    // Bumped on each press, so a switch reads back at once while the new answer is on its way.
    var presses by remember { mutableIntStateOf(0) }
    // Asked afresh on opening, even while lines are turned off after a crash: reading the
    // sources' status is the guarded part, and the dialog is where the reader looks for why.
    LaunchedEffect(Unit) { IoriLinesRepository.refresh(context) }

    MaterialTheme(colorScheme = monochrome) {
        EInkDialog(onDismiss = onDismiss) {
            Text(
                text = stringResource(R.string.iori_lines_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))

            if (tripped) {
                Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onTurnBackOn).padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.iori_lines_tripped_dialog), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
            }

            when (snapshot.glance) {
                GlanceState.NOT_INSTALLED -> Line(stringResource(R.string.iori_glance_not_installed))
                GlanceState.NEEDS_UPDATE -> Line(stringResource(R.string.iori_glance_needs, IoriLinesRepository.GLANCE_NEEDED))
                GlanceState.REFUSED -> Line(stringResource(R.string.iori_glance_refused))
                GlanceState.NO_ANSWER -> Line(stringResource(R.string.iori_glance_no_answer))
                GlanceState.UNKNOWN -> Line(stringResource(R.string.iori_glance_unknown))
                GlanceState.OK -> {
                    if (snapshot.sources.isEmpty()) Line(stringResource(R.string.iori_lines_none))
                    snapshot.sources.forEach { source ->
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.edit().putBoolean(Iori.lineKey(source.pkg), !onHere).apply()
                                    presses++
                                    IoriLinesRepository.refresh(context)
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
                                text = statusText(status, source.label) + (fault?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                        if (BuildConfig.DEBUG) {
                            Text(
                                text = stringResource(R.string.iori_debug_fault_source),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .clickable {
                                        val next = Fault.CYCLE[(Fault.CYCLE.indexOf(fault) + 1) % Fault.CYCLE.size]
                                        prefs.edit().putString(Iori.faultKey(source.pkg), next).apply()
                                        presses++
                                        IoriLinesRepository.refresh(context)
                                    }
                                    .padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.iori_debug_crash_own),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable {
                        Iori.prefs(context).edit().putString(Iori.KEY_HOME_FAULT, HomeFault.OWN).commit()
                        onDismiss()
                    }.padding(vertical = 4.dp),
                )
                Text(
                    text = stringResource(R.string.iori_debug_crash_upstream),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable {
                        Iori.prefs(context).edit().putString(Iori.KEY_HOME_FAULT, HomeFault.UPSTREAM).commit()
                        onDismiss()
                    }.padding(vertical = 4.dp),
                )
            }

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

@Composable
private fun Line(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 5.dp))
}

@Composable
private fun statusText(status: Status, label: String): String = when (status) {
    Status.ON -> stringResource(R.string.iori_status_on)
    Status.OFF_HERE -> stringResource(R.string.iori_status_off_here)
    Status.NOT_INSTALLED -> stringResource(R.string.iori_status_not_installed)
    Status.NEEDS_UPDATE -> stringResource(R.string.iori_status_needs_update)
    Status.OFF_APP -> stringResource(R.string.iori_status_off_app, label)
    Status.OFF_GLANCE -> stringResource(R.string.iori_status_off_glance)
    Status.NO_ANSWER -> stringResource(R.string.iori_status_no_answer)
}
