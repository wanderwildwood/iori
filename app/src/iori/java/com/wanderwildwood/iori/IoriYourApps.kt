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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.R
import com.wanderwildwood.iori.IoriAppInstaller.Job
import com.wanderwildwood.iori.IoriAppInstaller.ListState
import com.wanderwildwood.iori.IoriAppInstaller.Step
import com.wanderwildwood.iori.IoriAppsManifest.Entry
import com.wanderwildwood.iori.IoriAppsManifest.Status
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.houseTypography
import com.wanderwildwood.iori.ui.monochrome

/**
 * "Your apps", the one page for the house's apps (opened from its Settings row under Apps):
 * the apps of the list on wanderthe.dev in a folder for each section (Core first, the sections
 * from Home's own list, [IoriApps]); each folder says how many of its apps are on the phone, or
 * how many can be updated or are being got. In a folder, each app with what it is, the version on
 * the phone or "Not installed", how an install is going, and Install, Update or Up to date.
 * With no list from wanderthe.dev, fetched or kept, it shows Home's own list ([IoriApps]) in the
 * same folders, each app with what is on the phone and no Install or Update: there is nothing
 * to install from. The work is [IoriAppInstaller]'s; this is only the page.
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
        // A folder of the list, opened from its row: null is the list of folders.
        var open by remember { mutableStateOf<Int?>(null) }
        val sections = (list as? ListState.Ready)?.apps.orEmpty().let { apps ->
            IoriApps.sections(apps) { IoriApps.sectionOf(context, it.pkg, it.core) }
        }
        val builtIn = (list as? ListState.BuiltIn)?.apps.orEmpty().let { IoriApps.sections(it) }
        val inFolder = sections.firstOrNull { it.first == open }
        val inBuiltInFolder = builtIn.firstOrNull { it.first == open }
        val back = { if (inFolder != null || inBuiltInFolder != null) open = null else onClose() }

        MaterialTheme(colorScheme = monochrome, typography = houseTypography) {
            EInkDialog(onDismiss = back) {
                Text(
                    text = stringResource(inFolder?.first ?: inBuiltInFolder?.first ?: R.string.iori_your_apps_title),
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
                        // The phone is asked again after a resume (`looked`) and when an install
                        // ends, not at every download percent.
                        val ended = jobs.filterValues { it.step == Step.INSTALLED }.keys
                        val installed = remember(now.apps, ended, looked) {
                            now.apps.associate { it.pkg to IoriAppInstaller.installed(context, it.pkg) }
                        }
                        // The rows, drawn through FittedPages so the dialog ends under its last
                        // row instead of always filling the screen (PagedColumn alone takes all
                        // the height it is offered).
                        val rows = buildList<Pair<String, @Composable () -> Unit>> {
                            if (inFolder != null) {
                                inFolder.second.forEach { entry ->
                                    add(entry.pkg to { AppRow(entry, installed[entry.pkg], jobs[entry.pkg] ?: Job()) })
                                }
                            } else {
                                val problem = now.problem
                                if (problem != null) {
                                    add("problem" to {
                                        Column(Modifier.padding(bottom = 12.dp)) {
                                            Words(stringResource(R.string.iori_your_apps_from_copy, problem))
                                            Spacer(Modifier.height(8.dp))
                                            Press(stringResource(R.string.iori_your_apps_try_again), Modifier.fillMaxWidth()) {
                                                IoriAppInstaller.refresh(context)
                                            }
                                        }
                                    })
                                }
                                if (!allowed) {
                                    add("allow" to {
                                        Column(Modifier.padding(bottom = 12.dp)) {
                                            Words(stringResource(R.string.iori_your_apps_allow_body))
                                            Spacer(Modifier.height(8.dp))
                                            Press(stringResource(R.string.iori_your_apps_allow_button), Modifier.fillMaxWidth()) {
                                                IoriAppInstaller.askToAllow(context)
                                            }
                                        }
                                    })
                                }
                                if (now.apps.isEmpty()) {
                                    add("empty" to { Words(stringResource(R.string.iori_your_apps_empty)) })
                                }
                                sections.forEach { (head, inSection) ->
                                    add("folder:$head" to {
                                        val busy = inSection.count { e ->
                                            val step = jobs[e.pkg]?.step
                                            (step != null && step != Step.IDLE && step != Step.INSTALLED) ||
                                                IoriAppsManifest.status(e, installed[e.pkg], Build.VERSION.SDK_INT) == Status.UPDATE
                                        }
                                        FolderRow(head, inSection.count { installed[it.pkg] != null }, inSection.size, busy) { open = head }
                                    })
                                }
                            }
                        }
                        // A folder opens on its first page.
                        // At most the screen less a margin above and below, as the choice dialogs
                        // are: a long folder (Core) turns in pages inside a card, never a full screen.
                        val room = LocalConfiguration.current.screenHeightDp.dp - 200.dp
                        androidx.compose.runtime.key(open) {
                            FittedPages(rows, Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = room.coerceAtLeast(200.dp)))
                        }
                    }
                    is ListState.BuiltIn -> {
                        val installed = remember(now.apps, jobs.filterValues { it.step == Step.INSTALLED }.keys, looked) {
                            now.apps.associate { it.pkg to IoriAppInstaller.installed(context, it.pkg) }
                        }
                        val rows = buildList<Pair<String, @Composable () -> Unit>> {
                            if (inBuiltInFolder != null) {
                                inBuiltInFolder.second.forEach { app ->
                                    add(app.pkg to { KnownAppRow(app, installed[app.pkg], jobs[app.pkg] ?: Job()) })
                                }
                            } else {
                                add("problem" to {
                                    Column(Modifier.padding(bottom = 12.dp)) {
                                        Words(stringResource(R.string.iori_your_apps_built_in, now.why))
                                        Spacer(Modifier.height(8.dp))
                                        Press(stringResource(R.string.iori_your_apps_try_again), Modifier.fillMaxWidth()) {
                                            IoriAppInstaller.refresh(context)
                                        }
                                    }
                                })
                                builtIn.forEach { (head, inSection) ->
                                    add("folder:$head" to {
                                        FolderRow(head, inSection.count { installed[it.pkg] != null }, inSection.size, busy = 0) { open = head }
                                    })
                                }
                            }
                        }
                        val room = LocalConfiguration.current.screenHeightDp.dp - 200.dp
                        androidx.compose.runtime.key(open) {
                            FittedPages(rows, Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = room.coerceAtLeast(200.dp)))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(if (inFolder != null || inBuiltInFolder != null) R.string.iori_back else R.string.iori_close),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.onSurface)
                        .semantics { role = Role.Button }
                        .clickable(onClick = back)
                        .padding(vertical = 12.dp),
                )
            }
        }
    }

    /**
     * One folder of the list: its name, and on the right how many of its apps are on the phone,
     * or, in bold, how many can be updated or are being got right now.
     */
    @Composable
    private fun FolderRow(head: Int, onPhone: Int, total: Int, busy: Int, onOpen: () -> Unit) {
        val context = LocalContext.current
        val status = if (busy > 0) context.resources.getQuantityString(R.plurals.iori_apps_folder_updates, busy, busy)
        else stringResource(R.string.iori_apps_folder_count, onPhone, total)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { role = Role.Button }
                .clickable(onClick = onOpen)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = stringResource(head),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (busy > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
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

    /**
     * One app of Home's own list, shown when there is no list from wanderthe.dev: its name, and
     * the version on the phone or "Not installed". No press: there is nothing to install from.
     */
    @Composable
    private fun KnownAppRow(app: IoriApps.App, installed: IoriAppsManifest.Installed?, job: Job) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(app.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            val version = if (installed == null) stringResource(R.string.iori_your_apps_not_installed)
            else stringResource(R.string.iori_your_apps_installed_version, installed.versionName ?: installed.versionCode.toString())
            val line = buildList {
                if (app.core) add(stringResource(R.string.iori_your_apps_core))
                add(version)
            }.joinToString(" · ")
            Text(line, style = MaterialTheme.typography.labelSmall)
            outcome(job)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
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

/**
 * What an install is doing or why it stopped, in a few words, for the small rows outside Your
 * apps (the first run, the hand-off offer, the lines list); null when nothing is going on.
 */
@Composable
internal fun shortStatus(job: Job?): String? {
    if (job == null) return null
    return when (job.step) {
        Step.DOWNLOADING -> stringResource(R.string.iori_apps_downloading, job.percent)
        Step.CHECKING -> stringResource(R.string.iori_apps_checking)
        Step.INSTALLING -> stringResource(R.string.iori_apps_installing)
        Step.NEEDS_TAP -> stringResource(R.string.iori_apps_confirm)
        Step.FAILED -> stringResource(job.short ?: R.string.iori_apps_failed_refused)
        Step.IDLE, Step.INSTALLED -> null
    }
}

/** Runs [block] each time the screen this is on comes back in front. */
@Composable
internal fun OnResume(block: () -> Unit) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val latest by androidx.compose.runtime.rememberUpdatedState(block)
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) latest()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
