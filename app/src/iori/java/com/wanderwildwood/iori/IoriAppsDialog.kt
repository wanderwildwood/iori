package com.wanderwildwood.iori

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsHomeItem
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.PagedColumn
import com.wanderwildwood.iori.ui.houseTypography
import com.wanderwildwood.iori.ui.monochrome

/**
 * The Settings row for "Ecosystem apps", and the page it opens: a folder for each section (the
 * core set first), and in each folder its apps with what each is doing. Pressing one that is missing gets it; pressing one with an update
 * updates it; pressing one that is up to date opens it.
 */
object IoriAppsRow {

    @Composable
    fun SettingsRow(titleFontSize: TextUnit) {
        var open by remember { mutableStateOf(false) }
        SettingsHomeItem(
            title = stringResource(R.string.iori_apps_title),
            imageVector = Icons.Rounded.Apps,
            description = stringResource(R.string.iori_apps_row_desc),
            titleFontSize = titleFontSize,
            onClick = { open = true },
        )
        if (open) AppsDialog(onDismiss = { open = false })
    }
}

@Composable
fun AppsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { IoriApps.apps(context) }
    val states by IoriApps.states.collectAsState()
    LaunchedEffect(Unit) {
        IoriApps.refreshInstalled(context)
        IoriApps.checkLatest(context)
    }
    OnResume { IoriApps.resumed(context) }
    // A folder of the list, opened from its row (11-19): null is the list of folders.
    var open by remember { mutableStateOf<Int?>(null) }
    val sections = IoriApps.sections(apps)
    val inFolder = sections.firstOrNull { it.first == open }
    val back = { if (open != null) open = null else onDismiss() }

    MaterialTheme(colorScheme = monochrome, typography = houseTypography) {
        EInkDialog(onDismiss = back) {
            Text(
                text = stringResource(inFolder?.first ?: R.string.iori_apps_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            // The list takes the room the title and the foot leave, and turns its pages on whole rows;
            // a folder opens on its first page.
            androidx.compose.runtime.key(open) {
                PagedColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    if (inFolder == null) {
                        sections.forEach { (head, inSection) ->
                            item(key = "folder:$head") { FolderRow(head, inSection, states) { open = head } }
                        }
                    } else {
                        inFolder.second.forEach { app ->
                            item(key = app.pkg) { AppRow(app, states[app.pkg] ?: IoriApps.State()) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(if (inFolder != null) R.string.iori_back else R.string.iori_close),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Black)
                    .clickable(onClick = back)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/**
 * One folder of the list: its name, and on the right how many of its apps are on the phone, or,
 * in bold, how many can be updated or are being got right now.
 */
@Composable
private fun FolderRow(head: Int, apps: List<IoriApps.App>, states: Map<String, IoriApps.State>, onOpen: () -> Unit) {
    val context = LocalContext.current
    val ofThem = apps.map { states[it.pkg] ?: IoriApps.State() }
    val busy = ofThem.count { it.canUpdate || it.step != IoriApps.Step.IDLE }
    val status = if (busy > 0) context.resources.getQuantityString(R.plurals.iori_apps_folder_updates, busy, busy)
    else stringResource(R.string.iori_apps_folder_count, ofThem.count { it.installed != null }, apps.size)
    Row(
        modifier = Modifier
            .fillMaxWidth()
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

@Composable
private fun AppRow(app: IoriApps.App, state: IoriApps.State) {
    val context = LocalContext.current
    val status = progressText(state) ?: when {
        state.installed == null -> stringResource(R.string.iori_apps_get)
        state.canUpdate -> stringResource(R.string.iori_apps_update, state.latest ?: "")
        else -> state.installed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when {
                    state.installed == null || state.canUpdate || state.step == IoriApps.Step.FAILED ->
                        IoriApps.install(context, app)
                    else -> context.packageManager.getLaunchIntentForPackage(app.pkg)?.let {
                        runCatching { context.startActivity(it) }
                    }
                }
            }
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (state.installed == null || state.canUpdate) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** What an install is doing or why it stopped, as a row says it; null when nothing is going on. */
@Composable
internal fun progressText(state: IoriApps.State): String? = when (state.step) {
    IoriApps.Step.DOWNLOADING -> stringResource(R.string.iori_apps_downloading, state.percent)
    IoriApps.Step.CHECKING -> stringResource(R.string.iori_apps_checking)
    IoriApps.Step.INSTALLING -> stringResource(R.string.iori_apps_installing)
    IoriApps.Step.CONFIRM -> stringResource(R.string.iori_apps_confirm)
    IoriApps.Step.FAILED -> stringResource(
        when (state.failure) {
            IoriApps.Failure.NOT_ALLOWED -> R.string.iori_apps_failed_allow
            IoriApps.Failure.NETWORK -> R.string.iori_apps_failed_network
            IoriApps.Failure.CHECKSUM -> R.string.iori_apps_failed_checksum
            IoriApps.Failure.WRONG_APP -> R.string.iori_apps_failed_app
            IoriApps.Failure.WRONG_KEY -> R.string.iori_apps_failed_key
            else -> R.string.iori_apps_failed_refused
        }
    )
    IoriApps.Step.IDLE -> null
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
