package com.wanderwildwood.iori

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.helper.isinkosDefault
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsHomeItem
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsTitle
import com.github.gezimos.inkos.ui.dialogs.ComposeDialogManager

/**
 * Home's Settings (makeover piece 2). The top page is Home's own; inkOS's pages are reused under
 * it as they are, not rewritten. `SettingsFragment` calls [Top] for its home page, and draws
 * inkOS's own rows only on the Home screen sub-page, which [Foot] closes.
 *
 *  - **Phone-wide**: Dark mode, Nextcloud account, Default apps. Font and size come here later.
 *  - **Apps**: Your apps, one row that opens [IoriYourApps].
 *  - **Home**: Home screen (inkOS's Home, App drawer, Fonts, Notifications, Extras, then Folders
 *    and Home lines), Look & feel, Gestures, Advanced: inkOS's own pages.
 *  - **Setup and about**: Run setup again (the first run from its first page; nothing is reset)
 *    and About Home, which keeps the credit to gezimos and opens inkOS's own About.
 */
object IoriSettings {

    /** The pages the top page opens; all but Home screen are inkOS's own, as they are. */
    enum class Sub { HomeScreen, LookFeel, Gestures, Advanced, UpstreamAbout }

    @Composable
    fun Top(titleFontSize: TextUnit, setHomeTheme: (Constants.Theme) -> Unit, open: (Sub) -> Unit, onRunSetup: () -> Unit) {
        val context = LocalContext.current
        Column(Modifier.fillMaxWidth()) {
            SettingsTitle(text = stringResource(R.string.iori_settings_phone), fontSize = titleFontSize)
            IoriDark.SettingsRow(titleFontSize, setHomeTheme)
            IoriNextcloudUi.SettingsRow(titleFontSize)
            IoriDefaults.SettingsRow(titleFontSize)

            SettingsTitle(text = stringResource(R.string.iori_settings_apps), fontSize = titleFontSize)
            YourAppsRow(titleFontSize)

            SettingsTitle(text = stringResource(R.string.iori_settings_home_group), fontSize = titleFontSize)
            SettingsHomeItem(
                title = stringResource(R.string.iori_settings_home),
                description = stringResource(R.string.iori_settings_home_desc),
                titleFontSize = titleFontSize,
                onClick = { open(Sub.HomeScreen) },
            )
            SettingsHomeItem(
                title = stringResource(R.string.settings_look_feel_title),
                description = stringResource(R.string.desc_look_feel_settings),
                titleFontSize = titleFontSize,
                onClick = { open(Sub.LookFeel) },
            )
            SettingsHomeItem(
                title = stringResource(R.string.settings_gestures_title),
                description = stringResource(R.string.desc_gestures_settings),
                titleFontSize = titleFontSize,
                onClick = { open(Sub.Gestures) },
            )
            SettingsHomeItem(
                title = stringResource(R.string.settings_advanced_title),
                // Not being the phone's home screen is said in words, not with inkOS's star.
                description = stringResource(if (isinkosDefault(context)) R.string.desc_advanced_settings else R.string.iori_not_default),
                titleFontSize = titleFontSize,
                onClick = { open(Sub.Advanced) },
            )

            SettingsTitle(text = stringResource(R.string.iori_settings_setup), fontSize = titleFontSize)
            SettingsHomeItem(
                title = stringResource(R.string.iori_setup_again),
                imageVector = Icons.Rounded.RestartAlt,
                description = stringResource(R.string.iori_setup_again_desc),
                titleFontSize = titleFontSize,
                onClick = onRunSetup,
            )
            IoriAbout.AboutRow(titleFontSize) { open(Sub.UpstreamAbout) }
            Spacer(Modifier.height(16.dp))
        }
    }

    /** The foot of the Home screen sub-page: Folders and Home lines, after inkOS's rows. */
    @Composable
    fun Foot(titleFontSize: TextUnit, dialogs: ComposeDialogManager) {
        IoriFolders.SettingsRow(titleFontSize, dialogs)
        IoriAbout.LinesRow(titleFontSize)
    }

    /** Your apps: the one row for the family's apps; it opens [IoriYourApps]. */
    @Composable
    private fun YourAppsRow(titleFontSize: TextUnit) {
        var open by remember { mutableStateOf(false) }
        SettingsHomeItem(
            title = stringResource(R.string.iori_your_apps_title),
            imageVector = Icons.Rounded.Apps,
            description = stringResource(R.string.iori_your_apps_desc),
            titleFontSize = titleFontSize,
            onClick = { open = true },
        )
        if (open) IoriYourApps.Dialog(onClose = { open = false })
    }
}
