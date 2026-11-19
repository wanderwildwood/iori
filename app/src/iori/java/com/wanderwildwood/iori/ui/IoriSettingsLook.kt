package com.wanderwildwood.iori.ui

import android.graphics.Typeface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.github.gezimos.inkos.helper.getTrueSystemFont
import com.github.gezimos.inkos.style.LocalPrefs
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * The house look for inkOS's Settings pages (decided 11-18: every Settings page, inkOS's
 * sub-pages too, drawn like the other apps' Settings). inkOS's pages keep their rows and their
 * order; only the shared row pieces in `SettingsComposable` change, through `// iori:` hooks:
 * no icon on a row, the value under the label, a plain bold heading with no line and no
 * capitals, no separator lines, a switch like the other apps' switches.
 *
 * Sizes are the house scale (label 20, value 16, heading 16 bold, page title 20) at inkOS's
 * default Settings text size on the Kompakt, and grow or shrink with that setting, never below
 * the house floor of 16 sp. inkOS hands its rows `settingsSize × 1.5` sp, so [ratio] reads the
 * setting back from that.
 */
object IoriSettingsLook {
    /** Whether a Settings row shows inkOS's icon beside its label: not in the house look. */
    const val ROW_ICONS = false

    /**
     * What inkOS hands a row at its default Settings text size on the Kompakt (13 × 1.5): its
     * screen is about 320 dp wide, so the default comes from values-sw300dp. Wider phones start
     * a step or two higher and their rows come out a little larger, as inkOS's did.
     */
    private const val INKOS_DEFAULT_ROW_SP = 19.5f

    private fun ratio(rowFontSize: TextUnit): Float =
        if (rowFontSize.isSpecified) rowFontSize.value / INKOS_DEFAULT_ROW_SP else 1f

    private fun sized(house: Float, rowFontSize: TextUnit): TextUnit =
        (house * ratio(rowFontSize)).coerceAtLeast(HOUSE_TYPE_FLOOR_SP).sp

    /** A row's label. */
    fun label(rowFontSize: TextUnit): TextUnit = sized(20f, rowFontSize)

    /** The line under a label: the row's value, or its note. */
    fun value(rowFontSize: TextUnit): TextUnit = sized(16f, rowFontSize)

    /**
     * A group's heading. SettingsTitle is handed the row size already, so it is read the same way;
     * a heading with no size given takes the house one.
     */
    fun heading(rowFontSize: TextUnit): TextUnit = sized(16f, rowFontSize)

    /** The title at the top of a page, beside Back. */
    fun pageTitle(rowFontSize: TextUnit): TextUnit = sized(20f, rowFontSize)

    /**
     * The house switch: on is a filled track with a light thumb, off an outlined track with a
     * dark thumb, in the page's own two colors, so it reads the same in dark.
     */
    @Composable
    fun HouseSwitch(checked: Boolean, ink: Color, paper: Color, modifier: Modifier = Modifier) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = paper,
                checkedTrackColor = ink,
                checkedBorderColor = ink,
                uncheckedThumbColor = ink,
                uncheckedTrackColor = paper,
                uncheckedBorderColor = ink,
                disabledCheckedThumbColor = paper,
                disabledCheckedTrackColor = ink.copy(alpha = 0.4f),
                disabledUncheckedThumbColor = ink.copy(alpha = 0.4f),
                disabledUncheckedTrackColor = paper,
                disabledUncheckedBorderColor = ink.copy(alpha = 0.4f),
            ),
        )
    }

    /**
     * The Settings font made bold. inkOS hands its font to Compose as one typeface, and Compose
     * then ignores a weight, so a heading set Bold came out regular; Android's own bold of the
     * same typeface does carry it (made up from the regular when the font has no bold).
     */
    @Composable
    fun boldFamily(): FontFamily? {
        val prefs = LocalPrefs.current ?: return null
        val context = LocalContext.current
        val font = prefs.getFontForContext("settings")
        val path = prefs.getCustomFontPathForContext("settings")
        return remember(font, path) {
            val regular = font.getFont(context, path) ?: getTrueSystemFont()
            FontFamily(Typeface.create(regular, Typeface.BOLD))
        }
    }
}
