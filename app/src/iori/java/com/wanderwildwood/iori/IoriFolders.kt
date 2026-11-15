package com.wanderwildwood.iori

import android.content.Context
import android.view.View
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Folders
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsHomeItem
import com.github.gezimos.inkos.ui.dialogs.ComposeDialogManager

/**
 * The drawer's folders, from Settings: every folder in one list, to rename or delete, with a
 * new one and the starter folders at the foot. Putting an app in a folder is done in the drawer
 * (hold the app, Folder); this is the place to see and tidy them.
 */
object IoriFolders {

    @Composable
    fun SettingsRow(titleFontSize: TextUnit, dialogs: ComposeDialogManager) {
        val context = LocalContext.current
        val view = LocalView.current
        SettingsHomeItem(
            title = stringResource(R.string.folders),
            imageVector = Icons.Rounded.Folder,
            description = stringResource(R.string.folders_row_desc),
            titleFontSize = titleFontSize,
            onClick = { showList(context, view, dialogs) },
        )
    }

    private fun showList(context: Context, view: View, dialogs: ComposeDialogManager) {
        val prefs = Prefs(context)
        val folders = Folders.load(prefs).sortedBy { it.name.lowercase() }
        val newFolder = context.getString(R.string.folder_new)
        val starters = context.getString(R.string.folder_starters)
        val options = folders.map {
            context.resources.getQuantityString(R.plurals.folder_with_count, it.apps.size, it.name, it.apps.size)
        } + newFolder + starters
        dialogs.showSingleChoiceDialog(
            context = context,
            options = options.toTypedArray(),
            titleResId = R.string.folders,
            maxHeightRatio = 0.6f,
            onItemSelected = { choice ->
                val index = options.indexOf(choice)
                // Each next sheet is shown once this one has closed, or its closing would take it too.
                when {
                    index in folders.indices -> view.post { showFolder(context, view, dialogs, folders[index]) }
                    choice == starters -> {
                        val pm = context.packageManager
                        val onPhone = Folders.starterPackages.filter { pm.getLaunchIntentForPackage(it) != null }.toSet()
                        Folders.addStarters(prefs, onPhone)
                        view.post { showList(context, view, dialogs) }
                    }
                    else -> view.post {
                        dialogs.showInputDialog(context, context.getString(R.string.folder_name), "") { name ->
                            Folders.create(prefs, name)
                            view.post { showList(context, view, dialogs) }
                        }
                    }
                }
            },
        )
    }

    /** Delete asks in its own row: pressed once it reads "tap again"; anything else leaves the folder be. */
    private fun showFolder(
        context: Context, view: View, dialogs: ComposeDialogManager, folder: Folders.Folder, armed: Boolean = false,
    ) {
        val prefs = Prefs(context)
        val rename = context.getString(R.string.rename)
        val delete = context.getString(if (armed) R.string.folder_delete_armed else R.string.folder_delete)
        dialogs.showSingleChoiceDialog(
            context = context,
            options = arrayOf(rename, delete),
            titleResId = R.string.folder,
            onItemSelected = { choice ->
                if (choice == delete && !armed) {
                    view.post { showFolder(context, view, dialogs, folder, armed = true) }
                } else if (choice == delete) {
                    Folders.delete(prefs, folder.id)
                    view.post { showList(context, view, dialogs) }
                } else {
                    view.post {
                        dialogs.showInputDialog(context, context.getString(R.string.folder_name), folder.name) { name ->
                            Folders.rename(prefs, folder.id, name)
                            view.post { showList(context, view, dialogs) }
                        }
                    }
                }
            },
        )
    }
}
