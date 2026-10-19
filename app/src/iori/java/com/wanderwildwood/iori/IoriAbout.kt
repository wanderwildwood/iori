package com.wanderwildwood.iori

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.github.gezimos.inkos.BuildConfig
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.style.SettingsTheme
import com.github.gezimos.inkos.style.Theme
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsHomeItem
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.monochrome

/**
 * The About for inkOS:wndr: a row on the Settings home page, under inkOS's own About, which
 * keeps gezimos's credits and support links where he put them. Beside it, a row for the home
 * lines dialog, so the lines' status can be seen and changed even when home has no lines.
 */
object IoriAbout {

    const val SOURCE = "https://github.com/wanderwildwood/iori"
    private const val DONATE = "https://square.link/u/AGu8oT10"

    /** gezimos's own Buy Me a Coffee, which upstream's header button opened. */
    private const val COFFEE = "https://buymeacoffee.com/gezimos"

    /**
     * In the Settings header, where upstream has its Buy Me a Coffee button: a cup that still
     * opens gezimos's page, and a llama that opens this app's. Upstream's pill, border and focus
     * look, with a glyph the height of the words it replaces.
     */
    @Composable
    fun HeaderButtons(shape: Shape, fontSize: TextUnit) {
        val size = if (fontSize.isSpecified) fontSize else SettingsTheme.typography.title.fontSize
        val glyph = with(LocalDensity.current) { (if (size.isSpecified) size * 1.25f else 20.sp).toDp() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(R.drawable.iori_support_coffee, stringResource(R.string.iori_support_coffee), COFFEE, shape, glyph)
            HeaderButton(R.drawable.iori_llama, stringResource(R.string.iori_support_llama), DONATE, shape, glyph)
        }
    }

    @Composable
    private fun HeaderButton(@DrawableRes icon: Int, description: String, url: String, shape: Shape, glyph: Dp) {
        val context = LocalContext.current
        val interactionSource = remember { MutableInteractionSource() }
        val focused = interactionSource.collectIsFocusedAsState().value
        val text = Theme.colors.text
        val background = Theme.colors.background
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (focused) background else text,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(shape)
                .background(if (focused) text else Color.Transparent)
                .border(width = 2.dp, color = text, shape = shape)
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button) { open(context, url) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .size(glyph),
        )
    }

    @Composable
    fun SettingsRow(titleFontSize: TextUnit) {
        val context = LocalContext.current
        var lines by remember { mutableStateOf(false) }
        var tripped by remember { mutableStateOf(false) }
        SettingsHomeItem(
            title = stringResource(R.string.iori_lines_title),
            imageVector = Icons.Rounded.ViewHeadline,
            description = stringResource(R.string.iori_lines_row_desc),
            titleFontSize = titleFontSize,
            onClick = {
                tripped = Iori.tripped(context)
                lines = true
            },
        )
        if (lines) {
            LinesDialog(
                tripped = tripped,
                onTurnBackOn = {
                    Iori.clearTripped(context)
                    tripped = false
                    IoriLinesRepository.refresh(context)
                },
                onDismiss = { lines = false },
            )
        }
        var open by remember { mutableStateOf(false) }
        SettingsHomeItem(
            title = stringResource(R.string.iori_about_row),
            imageVector = Icons.Rounded.Info,
            description = stringResource(R.string.iori_about_row_desc),
            titleFontSize = titleFontSize,
            onClick = { open = true },
        )
        if (open) AboutDialog(onDismiss = { open = false })
    }

    /** In the house order: name and version, what it is based on, licence, bundled code, source, llama. */
    @Composable
    fun AboutDialog(onDismiss: () -> Unit) {
        val context = LocalContext.current
        MaterialTheme(colorScheme = monochrome) {
            EInkDialog(onDismiss = onDismiss) {
                Text(
                    text = stringResource(R.string.iori_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                Small(stringResource(R.string.iori_about_based))
                Spacer(Modifier.height(10.dp))
                Small(stringResource(R.string.iori_about_licence))
                Spacer(Modifier.height(10.dp))
                Small(stringResource(R.string.iori_about_bundled))
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.clickable { open(context, SOURCE) }.padding(vertical = 4.dp)) {
                    Small(SOURCE.removePrefix("https://"))
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { open(context, DONATE) }.padding(vertical = 4.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.iori_llama),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Small(stringResource(R.string.iori_about_feed_llamas))
                }
                Spacer(Modifier.height(20.dp))
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
    private fun Small(text: String) {
        Text(text = text, style = MaterialTheme.typography.labelSmall)
    }

    /** Opens [url], and says so when nothing on the phone can. */
    private fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.iori_about_no_browser), Toast.LENGTH_SHORT).show()
        }
    }
}
