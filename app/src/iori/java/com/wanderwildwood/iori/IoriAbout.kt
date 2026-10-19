package com.wanderwildwood.iori

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.BuildConfig
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsHomeItem
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.monochrome

/**
 * The About for inkOS:wndr: one row on the Settings home page, under inkOS's own About, which
 * keeps gezimos's credits and support links where he put them.
 */
object IoriAbout {

    const val SOURCE = "https://github.com/wanderwildwood/iori"
    private const val DONATE = "https://square.link/u/AGu8oT10"

    @Composable
    fun SettingsRow(titleFontSize: TextUnit) {
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
