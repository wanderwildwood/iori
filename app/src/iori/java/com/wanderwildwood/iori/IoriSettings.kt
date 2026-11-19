package com.wanderwildwood.iori

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsHomeItem
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsTitle
import com.github.gezimos.inkos.ui.dialogs.ComposeDialogManager

/**
 * The order of the Settings home page. It is still inkOS's page, drawn with inkOS's rows and
 * headings, and inkOS's own rows are untouched in the middle of it; Home adds what is around
 * them, in two calls from `SettingsFragment`:
 *
 *  - [Top]: **Phone** (Dark mode, Nextcloud account), **Apps** (Ecosystem apps, Your apps, Default apps),
 *    then the heading **Home screen** over inkOS's rows.
 *  - [Foot]: Folders and Home lines, which belong to the home screen too, then **Setup and
 *    about**: Run setup again, and the one About.
 *
 * So what is true of the whole phone comes first, and the launcher's own settings are one group
 * under their own heading.
 */
object IoriSettings {

    @Composable
    fun Top(titleFontSize: TextUnit, setHomeTheme: (Constants.Theme) -> Unit) {
        SettingsTitle(text = stringResource(R.string.iori_settings_phone), fontSize = titleFontSize)
        IoriDark.SettingsRow(titleFontSize, setHomeTheme)
        IoriNextcloudUi.SettingsRow(titleFontSize)
        SettingsTitle(text = stringResource(R.string.iori_settings_apps), fontSize = titleFontSize)
        IoriAppsRow.SettingsRow(titleFontSize)
        IoriYourApps.SettingsRow(titleFontSize)
        IoriDefaults.SettingsRow(titleFontSize)
        SettingsTitle(text = stringResource(R.string.iori_settings_home), fontSize = titleFontSize)
    }

    /** [onRunSetup] opens the first run again at its first page; nothing is reset by it. */
    @Composable
    fun Foot(titleFontSize: TextUnit, dialogs: ComposeDialogManager, onUpstreamAbout: () -> Unit, onRunSetup: () -> Unit) {
        IoriFolders.SettingsRow(titleFontSize, dialogs)
        IoriAbout.LinesRow(titleFontSize)
        SettingsTitle(text = stringResource(R.string.iori_settings_setup), fontSize = titleFontSize)
        SettingsHomeItem(
            title = stringResource(R.string.iori_setup_again),
            imageVector = Icons.Rounded.RestartAlt,
            description = stringResource(R.string.iori_setup_again_desc),
            titleFontSize = titleFontSize,
            onClick = onRunSetup,
        )
        IoriAbout.AboutRow(titleFontSize, onUpstreamAbout)
    }
}
