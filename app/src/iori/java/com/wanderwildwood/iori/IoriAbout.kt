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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
import com.wanderwildwood.iori.ui.PagedColumn
import com.wanderwildwood.iori.ui.houseTypography
import com.wanderwildwood.iori.ui.licenceComponentsFromJson
import com.wanderwildwood.iori.ui.licenceParagraphs
import com.wanderwildwood.iori.ui.monochrome

/**
 * The About for Home: the one About row on the Settings home page. inkOS's own About page keeps
 * gezimos's credits and support links as gezimos wrote them and is opened from a row of this one.
 * Also here, the row for the home lines dialog, so the lines' status can be seen and changed even
 * when home has no lines. [IoriSettings] places both.
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

    private var open by mutableStateOf(false)

    /** The Home lines row of the Settings home page: the lines' status, seen and changed even when home has none. */
    @Composable
    fun LinesRow(titleFontSize: TextUnit) {
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
    }

    /**
     * The one About row, last on the Settings home page. inkOS's own About row is not drawn; its
     * page, with gezimos's credits and links, is a row of this About ([onUpstreamAbout] opens it).
     */
    @Composable
    fun AboutRow(titleFontSize: TextUnit, onUpstreamAbout: () -> Unit) {
        SettingsHomeItem(
            title = stringResource(R.string.iori_about_row),
            imageVector = Icons.Rounded.Info,
            description = stringResource(R.string.iori_about_row_desc),
            titleFontSize = titleFontSize,
            onClick = { open = true },
        )
        if (open) {
            AboutDialog(
                onUpstreamAbout = {
                    open = false
                    onUpstreamAbout()
                },
                onDismiss = { open = false },
            )
        }
    }

    /** A drawer of the About: its heading, and the paragraphs shown under it while it is open. */
    private class Drawer(val title: Int, val lines: List<Int>, val contact: Boolean = false)

    private val DRAWERS = listOf(
        Drawer(R.string.iori_about_sends_title, listOf(R.string.privacy_no_data_body, R.string.privacy_no_server_body)),
        Drawer(R.string.privacy_who_title, listOf(R.string.privacy_who)),
        Drawer(R.string.privacy_contact_title, listOf(R.string.privacy_contact), contact = true),
        Drawer(R.string.privacy_rights_title, listOf(R.string.privacy_rights)),
    )

    /** Home's own lines at the head of the Licenses drawer. */
    private val OWN_LICENSE = listOf(
        R.string.iori_about_licence, R.string.iori_about_based, R.string.iori_about_bundled, R.string.iori_about_mmd,
    )

    private class Entry(val key: String, val content: @Composable () -> Unit)

    /**
     * The full texts a library's license line calls for. Apache-2.0 is one text for all; the two
     * BSD texts are the libraries' own files (Glide's, protobuf's), since a BSD license names its
     * holder. A license with no text here shows its name and the library's address only.
     */
    internal fun textsFor(licence: String): List<Int> = buildList {
        if ("BSD-2-Clause" in licence) add(R.raw.glide_license)
        if ("BSD-3-Clause" in licence) add(R.raw.protobuf_license)
        if ("Apache" in licence) add(R.raw.apache_2_0)
    }

    private fun paragraphs(context: Context, raw: Int): List<String> = try {
        licenceParagraphs(context.resources.openRawResource(raw).bufferedReader().use { it.readText() })
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * In the house order: name and version, what it is, the source, the llama, then drawers that
     * start closed (what it sends, who we are, how to contact us, your rights), the way to inkOS's
     * own About, and last Licenses: Home's own lines and the full GPL, then one small drawer for
     * every library the build packed (the list is made by the build, `aboutlibraries.json`), each
     * with its license, its address and the full text. Every line is a row of the list, so pages
     * turn on whole rows.
     */
    @Composable
    fun AboutDialog(onUpstreamAbout: () -> Unit, onDismiss: () -> Unit) {
        val context = LocalContext.current
        var opened by remember { mutableStateOf(emptySet<String>()) }
        val list = rememberLazyListState()
        val libraries = remember {
            try {
                licenceComponentsFromJson(
                    context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
                ).sortedBy { it.name.lowercase() }
            } catch (_: Exception) {
                emptyList()
            }
        }
        val texts = remember { HashMap<Int, List<String>>() }
        fun text(raw: Int) = texts.getOrPut(raw) { paragraphs(context, raw) }

        // The row of the drawer pressed last. Opened where its first line does not fit at the foot,
        // it comes to the top, so the press is answered on the page it was made on.
        var pressed by remember { mutableStateOf(-1) }
        LaunchedEffect(pressed, opened) {
            if (pressed < 0) return@LaunchedEffect
            withFrameNanos { }
            val info = list.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull { it.index == pressed + 1 }
            if (first == null || first.offset + first.size > info.viewportEndOffset) list.scrollToItem(pressed)
            pressed = -1
        }

        val entries = ArrayList<Entry>()
        fun toggle(key: String, at: Int) {
            val wasOpen = key in opened
            opened = if (wasOpen) opened - key else opened + key
            if (!wasOpen) pressed = at
        }
        /** A drawer's heading row; returns whether the drawer is open. */
        fun drawer(key: String, title: @Composable () -> Unit): Boolean {
            val at = entries.size
            entries += Entry(key) { Heading(onClick = { toggle(key, at) }, content = title) }
            return key in opened
        }
        fun fullText(key: String, raw: Int) {
            text(raw).forEachIndexed { i, paragraph -> entries += Entry("$key:$raw:$i") { Line(paragraph) } }
        }

        entries += Entry("what") { Line(stringResource(R.string.iori_about_what)) }
        entries += Entry("source") { Line(SOURCE.removePrefix("https://"), Modifier.clickable { open(context, SOURCE) }) }
        entries += Entry("llama") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { open(context, DONATE) }.padding(vertical = 8.dp),
            ) {
                Image(painter = painterResource(R.drawable.iori_llama), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.iori_support_llama), style = MaterialTheme.typography.bodyMedium)
            }
        }
        DRAWERS.forEach { d ->
            val key = "drawer:${d.title}"
            if (drawer(key) { Bold(stringResource(d.title)) }) d.lines.forEach { line ->
                // The sentence that gives the address is the contact row: pressed, it writes.
                entries += Entry("$key:$line") {
                    Line(stringResource(line), if (d.contact) Modifier.clickable { write(context) } else Modifier)
                }
            }
        }
        entries += Entry("upstream") { Heading(onClick = onUpstreamAbout) { Bold(stringResource(R.string.iori_about_upstream)) } }
        if (drawer("licenses") { Bold(stringResource(R.string.iori_about_license_title)) }) {
            OWN_LICENSE.forEach { line -> entries += Entry("licenses:$line") { Line(stringResource(line)) } }
            if (drawer("gpl") { Line(stringResource(R.string.iori_about_gpl_text)) }) fullText("gpl", R.raw.gpl_3_0)
            libraries.forEach { lib ->
                val key = "lib:${lib.name}"
                val isOpen = drawer(key) {
                    Column {
                        Text(lib.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text(lib.author, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (isOpen) {
                    entries += Entry("$key:licence") { Line(lib.licence) }
                    if (lib.url.isNotBlank()) {
                        entries += Entry("$key:url") {
                            Line(lib.url.removePrefix("https://"), Modifier.clickable { open(context, lib.url) })
                        }
                    }
                    textsFor(lib.licence).forEach { fullText(key, it) }
                }
            }
        }

        MaterialTheme(colorScheme = monochrome, typography = houseTypography) {
            EInkDialog(onDismiss = onDismiss) {
                Text(
                    text = stringResource(R.string.iori_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                PagedColumn(Modifier.fillMaxWidth().weight(1f), state = list) {
                    entries.forEach { entry -> item(key = entry.key) { entry.content() } }
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
    private fun Bold(text: String) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }

    /** A full-width row at least 48 dp tall, no mark beside its words; a press opens or closes what is under it. */
    @Composable
    private fun Heading(onClick: () -> Unit, content: @Composable () -> Unit) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        ) { content() }
    }

    @Composable
    private fun Line(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
    }

    private const val ADDRESS = "email@wanderthe.dev"

    /** Opens the phone's email app on a new message to the author; says the address when there is none. */
    private fun write(context: Context) {
        val subject = context.getString(R.string.iori_about_version, BuildConfig.VERSION_NAME)
        try {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$ADDRESS?subject=${Uri.encode(subject)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.about_no_mail_app, ADDRESS), Toast.LENGTH_LONG).show()
        }
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
