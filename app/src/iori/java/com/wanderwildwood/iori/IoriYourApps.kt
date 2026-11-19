package com.wanderwildwood.iori

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.R
import com.wanderwildwood.iori.IoriAppInstaller.Job
import com.wanderwildwood.iori.IoriAppInstaller.ListState
import com.wanderwildwood.iori.IoriAppInstaller.Step
import com.wanderwildwood.iori.IoriAppsManifest.Entry
import com.wanderwildwood.iori.IoriAppsManifest.Status
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.PagedColumn
import com.wanderwildwood.iori.ui.houseTypography
import com.wanderwildwood.iori.ui.monochrome

/**
 * "Your apps": the house's apps from the list on wanderthe.dev (core apps first), each with
 * what it is, the version on the phone or "Not installed", and Install, Update or Up to date.
 * The work is [IoriAppInstaller]'s; this is only the page.
 */
object IoriYourApps {

    @Composable
    fun Dialog(onClose: () -> Unit) {
        val context = LocalContext.current
        val list by IoriAppInstaller.list.collectAsState()
        val jobs by IoriAppInstaller.jobs.collectAsState()
        // What is on the phone and whether Home may install are read again each time the page
        // comes back in front (after Android's install screen or its "Install unknown apps" screen).
        var looked by remember { mutableIntStateOf(0) }
        var allowed by remember { mutableStateOf(IoriAppInstaller.allowed(context)) }
        LaunchedEffect(Unit) {
            IoriAppInstaller.clearFinished()
            IoriAppInstaller.refresh(context)
        }
        OnResume {
            allowed = IoriAppInstaller.allowed(context)
            looked++
            IoriAppInstaller.resumed(context)
        }

        MaterialTheme(colorScheme = monochrome, typography = houseTypography) {
            EInkDialog(onDismiss = onClose) {
                Text(
                    text = stringResource(R.string.iori_your_apps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                when (val now = list) {
                    is ListState.Loading -> Words(stringResource(R.string.iori_your_apps_loading))
                    is ListState.Failed -> {
                        Words(now.why)
                        Spacer(Modifier.height(12.dp))
                        Press(stringResource(R.string.iori_your_apps_try_again), Modifier.fillMaxWidth()) {
                            IoriAppInstaller.refresh(context)
                        }
                    }
                    is ListState.Ready -> {
                        // `looked` is read so the rows ask the phone again after a resume.
                        val installed = remember(now.apps, jobs, looked) {
                            now.apps.associate { it.pkg to IoriAppInstaller.installed(context, it.pkg) }
                        }
                        PagedColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            if (now.problem != null) {
                                item(key = "problem") {
                                    Column(Modifier.padding(bottom = 12.dp)) {
                                        Words(stringResource(R.string.iori_your_apps_from_copy, now.problem))
                                        Spacer(Modifier.height(8.dp))
                                        Press(stringResource(R.string.iori_your_apps_try_again), Modifier.fillMaxWidth()) {
                                            IoriAppInstaller.refresh(context)
                                        }
                                    }
                                }
                            }
                            if (!allowed) {
                                item(key = "allow") {
                                    Column(Modifier.padding(bottom = 12.dp)) {
                                        Words(stringResource(R.string.iori_your_apps_allow_body))
                                        Spacer(Modifier.height(8.dp))
                                        Press(stringResource(R.string.iori_your_apps_allow_button), Modifier.fillMaxWidth()) {
                                            IoriAppInstaller.askToAllow(context)
                                        }
                                    }
                                }
                            }
                            if (now.apps.isEmpty()) {
                                item(key = "empty") { Words(stringResource(R.string.iori_your_apps_empty)) }
                            }
                            now.apps.forEach { entry ->
                                item(key = entry.pkg) {
                                    AppRow(entry, installed[entry.pkg], jobs[entry.pkg] ?: Job())
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.iori_close),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.onSurface)
                        .semantics { role = Role.Button }
                        .clickable(onClick = onClose)
                        .padding(vertical = 12.dp),
                )
            }
        }
    }

    /** One app: name, what it is, its version, how an install went, and its press on the right. */
    @Composable
    private fun AppRow(entry: Entry, installed: IoriAppsManifest.Installed?, job: Job) {
        val context = LocalContext.current
        val status = IoriAppsManifest.status(entry, installed, Build.VERSION.SDK_INT)
        val busy = job.step == Step.DOWNLOADING || job.step == Step.CHECKING || job.step == Step.INSTALLING
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (entry.summary.isNotEmpty()) {
                    Text(entry.summary, style = MaterialTheme.typography.bodySmall)
                }
                val version = if (installed == null) stringResource(R.string.iori_your_apps_not_installed)
                else stringResource(R.string.iori_your_apps_installed_version, installed.versionName ?: installed.versionCode.toString())
                val line = buildList {
                    if (entry.core) add(stringResource(R.string.iori_your_apps_core))
                    add(version)
                    if (status == Status.UPDATE) add(stringResource(R.string.iori_your_apps_new_version, entry.versionName))
                }.joinToString(" · ")
                Text(line, style = MaterialTheme.typography.labelSmall)
                outcome(job)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            val label = stringResource(
                when (status) {
                    Status.NOT_INSTALLED -> R.string.iori_your_apps_install
                    Status.UPDATE -> R.string.iori_your_apps_update
                    Status.UP_TO_DATE -> R.string.iori_your_apps_up_to_date
                    Status.TOO_OLD_ANDROID -> R.string.iori_your_apps_too_old
                }
            )
            val canPress = !busy && (status == Status.NOT_INSTALLED || status == Status.UPDATE)
            Press(label, enabled = canPress) { IoriAppInstaller.install(context, entry) }
        }
    }

    /** What an install is doing or how it ended, in words; null when nothing has happened. */
    @Composable
    private fun outcome(job: Job): String? = when (job.step) {
        Step.IDLE -> null
        Step.DOWNLOADING -> stringResource(R.string.iori_your_apps_downloading, job.percent)
        Step.CHECKING -> stringResource(R.string.iori_your_apps_checking)
        Step.INSTALLING -> stringResource(R.string.iori_your_apps_installing)
        Step.NEEDS_TAP -> stringResource(R.string.iori_your_apps_needs_tap)
        Step.INSTALLED -> stringResource(R.string.iori_your_apps_installed)
        Step.FAILED -> stringResource(R.string.iori_your_apps_failed, job.why ?: stringResource(R.string.iori_your_apps_why_android))
    }

    @Composable
    private fun Words(text: String) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }

    /**
     * A house press: a black rim, 8 dp corners, at least 48 dp tall. One that cannot be used has
     * a thin rim and no press, so "Up to date" reads as a state, not a button.
     */
    @Composable
    private fun Press(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
        val rim = MaterialTheme.colorScheme.onSurface
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .heightIn(min = 48.dp)
                .border(if (enabled) 2.dp else 1.dp, rim, RoundedCornerShape(8.dp))
                .then(if (enabled) Modifier.semantics { role = Role.Button }.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}
