package com.wanderwildwood.iori

import android.app.Activity
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.MainViewModel
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.AppListItem
import com.github.gezimos.inkos.data.Constants.Action
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.EinkHelper
import com.github.gezimos.inkos.style.UiScaleMode
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.houseTypography
import com.wanderwildwood.iori.ui.monochrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * inkOS's Settings dialogs in the house look (the owner's choice, 19 Nov 2026): an [EInkDialog] with
 * the setting's name as its title, where inkOS drew a bottom sheet with a small-caps title.
 *
 * - a number: the value large between big − and + buttons (inkOS's own lowest, highest and step),
 *   Cancel and Save across the foot; held, a button keeps stepping, faster after a while;
 * - a choice: the choices as a list in whole-row pages, the current one ticked; a press picks and
 *   closes. inkOS's rows that cycled through their choices in place open this too;
 * - a font: the fonts, each in its own face, the ones added from a file after them, and "Add a
 *   font file" at the end; no tabs and no ⓘ;
 * - a gesture's action: one list (Off, Home's actions, the system's, then the apps) in place of
 *   inkOS's three tabs.
 *
 * inkOS's `ComposeDialogManager` hands its calls here through `// iori:` lines; each one writes
 * exactly what inkOS's sheet wrote. One dialog is up at a time, as with inkOS's sheets, and when it
 * closes the manager's after-close step (Edit mode reopening its own sheet) runs once, as it did.
 */
class IoriDialogs(private val activity: Activity, private val afterClose: () -> Unit) {

    private var view: ComposeView? = null

    /** Closes the dialog that is up, if any, and runs the after-close step once. */
    fun dismiss() {
        val v = view ?: return
        view = null
        (v.parent as? ViewGroup)?.removeView(v)
        afterClose()
    }

    /** Shows [content]; false when there is nowhere to show it (inkOS's own sheet is used then). */
    private fun show(content: @Composable (close: () -> Unit) -> Unit): Boolean {
        if (view != null) return true // one at a time; inkOS's host ignores a second sheet the same way
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return false
        val v = ComposeView(activity).apply {
            setContent {
                MaterialTheme(colorScheme = monochrome, typography = houseTypography) {
                    content(::dismiss)
                }
            }
        }
        root.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view = v
        return true
    }

    /** inkOS's slider sheet. With [live] each step is written as it is made and the foot is Close. */
    fun number(
        title: String,
        min: Int,
        max: Int,
        current: Int,
        live: Boolean,
        step: Int,
        format: (Int) -> String,
        onValue: (Int) -> Unit,
    ): Boolean {
        if (max < min) return false
        return show { close -> NumberDialog(sentenceCase(title), min, max, current, live, step, format, onValue, close) }
    }

    /**
     * A list of choices. [selected] is ticked; [stayOpen] choices are answered without closing
     * (inkOS's "non-selectable" rows, like an "add" row). [fonts] draws each label in its own face.
     */
    fun choose(
        title: String,
        labels: List<String>,
        selected: Int?,
        fonts: List<Typeface>? = null,
        stayOpen: (Int) -> Boolean = { false },
        onPick: (Int) -> Unit,
    ): Boolean = show { close ->
        ChoiceDialog(
            title = sentenceCase(title),
            rows = labels.mapIndexed { i, label ->
                Choice.Pick(
                    key = "c$i:$label",
                    label = label,
                    chosen = i == selected && !stayOpen(i),
                    face = fonts?.getOrNull(i),
                    closes = !stayOpen(i),
                ) { onPick(i) }
            },
            close = close,
        )
    }

    /** inkOS's font picker: built-in fonts, then the added ones, then "Add a font file" (inkOS's "Add custom font..."). */
    fun fonts(
        title: String,
        builtIn: List<String>,
        builtInFaces: List<Typeface>,
        custom: List<String>,
        customFaces: List<Typeface>,
        selected: Int?,
        onAdd: (() -> Unit)?,
        onPick: (name: String, custom: Boolean) -> Unit,
        onRemove: ((name: String, close: () -> Unit) -> Unit)?,
    ): Boolean = show { close ->
        val rows = buildList {
            builtIn.forEachIndexed { i, name ->
                add(Choice.Pick("b$i:$name", name, selected == i, builtInFaces.getOrNull(i)) { onPick(name, false) })
            }
            custom.forEachIndexed { i, name ->
                add(
                    Choice.Pick(
                        "u$i:$name", name, selected == builtIn.size + i, customFaces.getOrNull(i),
                        remove = onRemove?.let { remove -> { remove(name, close) } },
                    ) { onPick(name, true) }
                )
            }
            if (onAdd != null) {
                add(Choice.Pick("add", activity.getString(R.string.iori_dialog_add_font), false, closes = false) { onAdd() })
            }
        }
        ChoiceDialog(sentenceCase(title), rows, close)
    }

    /** inkOS's gesture action sheet as one list: Off, Home's actions, the system's, the apps. */
    fun gesture(
        title: String,
        allowed: List<Action>,
        current: Action,
        currentAppLabel: String,
        viewModel: MainViewModel,
        onSelect: (Action, AppListItem?) -> Unit,
    ): Boolean = show { close ->
        val context = LocalContext.current
        val apps by viewModel.getAppList(includeHiddenApps = true, flag = null).collectAsState(initial = emptyList())
        val sorted = remember(apps) {
            val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
            apps.filter {
                val pkg = it.activityPackage
                pkg.isNotBlank() && !pkg.startsWith("com.inkos.internal.") &&
                    !(pkg == context.packageName && !it.shortcutId.isNullOrEmpty())
            }.sortedWith(compareBy(collator) { it.customLabel.ifEmpty { it.activityLabel } })
        }
        fun label(action: Action) =
            if (action == Action.OpenAppDrawer) context.getString(R.string.app_drawer) else action.getString(context)
        val rows = buildList {
            if (Action.Disabled in allowed) {
                add(Choice.Pick("off", context.getString(R.string.disabled), current == Action.Disabled) { onSelect(Action.Disabled, null) })
            }
            val own = Action.INKOS_ACTIONS.filter { it in allowed && it != Action.Disabled }
            if (own.isNotEmpty()) add(Choice.Head("h:own", context.getString(R.string.gesture_tab_inkos)))
            own.forEach { a -> add(Choice.Pick("a:${a.name}", label(a), current == a) { onSelect(a, null) }) }
            val system = Action.SYSTEM_ACTIONS.filter { it in allowed && it != Action.Disabled }
            if (system.isNotEmpty()) add(Choice.Head("h:system", context.getString(R.string.gesture_tab_system)))
            system.forEach { a -> add(Choice.Pick("s:${a.name}", label(a), current == a) { onSelect(a, null) }) }
            if (Action.OpenApp in allowed) {
                add(Choice.Head("h:apps", context.getString(R.string.open_app)))
                sorted.forEachIndexed { i, app ->
                    val name = app.customLabel.ifEmpty { app.activityLabel }
                    add(
                        Choice.Pick(
                            "p$i:${app.activityPackage}", name,
                            current == Action.OpenApp && currentAppLabel == name,
                        ) { onSelect(Action.OpenApp, app) }
                    )
                }
            }
        }
        ChoiceDialog(sentenceCase(title), rows, close)
    }

    /** Settings > Extras > E-ink mode (Kompakt only): the four modes in the order inkOS cycled them. */
    fun einkMode(title: String, current: Int, onPick: (Int) -> Unit): Boolean {
        val modes = buildList<Int> {
            var m = EinkHelper.MEINK_MODE_DISABLED
            do { add(m); m = EinkHelper.nextMode(m) } while (m != EinkHelper.MEINK_MODE_DISABLED && size < 8)
        }
        return choose(title, modes.map { EinkHelper.modeName(it) }, modes.indexOf(current)) { onPick(modes[it]) }
    }

    /** Settings > Look > UI scale, which inkOS drew as a sheet of its own; the same writes on a pick. */
    fun uiScale(prefs: Prefs, viewModel: MainViewModel, title: String): Boolean {
        val detected = com.github.gezimos.inkos.style.detectScaleMode(activity)
        val modes = UiScaleMode.entries.filter { it != UiScaleMode.AUTO }
        val active = UiScaleMode.fromId(prefs.uiScaleMode)
        val selected = modes.indexOf(if (active == UiScaleMode.AUTO) detected else active)
        val auto = activity.getString(R.string.iori_dialog_auto)
        val labels = modes.map { if (it == detected) "${it.label} ($auto)" else it.label }
        return choose(title, labels, selected) { i ->
            val mode = modes[i]
            prefs.uiScaleMode = if (mode == detected) 0 else mode.id
            val s = mode.scale
            viewModel.setSettingsSize((16 * s).toInt())
            viewModel.setClockSize((48 * s).toInt())
            viewModel.setAppSize((27 * s).toInt())
            viewModel.setDateSize((18 * s).toInt())
            viewModel.setQuoteSize((18 * s).toInt())
            viewModel.setNotificationsTextSize((18 * s).toInt())
            viewModel.setLettersTextSize((18 * s).toInt())
            viewModel.setLettersTitleSize((36 * s).toInt())
            viewModel.setAppDrawerSize((24 * s).toInt())
            viewModel.setTextPaddingSize((10 * s).toInt())
            viewModel.setAppDrawerGap((8 * s).toInt())
            viewModel.setTopWidgetMargin((32 * s).toInt())
            viewModel.setBottomWidgetMargin((32 * s).toInt())
            activity.recreate()
        }
    }

    companion object {
        /**
         * A title in sentence case. Home's strings already are; a few of inkOS's titles are written
         * in code in title case ("Wallpaper Visibility"). Only a title whose every word starts with
         * a capital is changed, and a word in capitals throughout (UI, AM) is left as it is.
         */
        fun sentenceCase(title: String): String {
            val words = title.split(' ')
            if (words.size < 2 || words.any { w -> w.firstOrNull()?.let { it.isLetter() && !it.isUpperCase() } == true }) return title
            return words.mapIndexed { i, w ->
                if (i == 0 || w.length < 2 || w.all { !it.isLetter() || it.isUpperCase() }) w else w.lowercase()
            }.joinToString(" ")
        }

        /** One step of a number dialog: [by] steps of [step] from [value], kept within [min]..[max]. */
        fun stepped(value: Int, by: Int, step: Int, min: Int, max: Int): Int =
            (value.toLong() + by.toLong() * step).coerceIn(min.toLong(), max.toLong()).toInt()
    }
}

/** A row of a choice dialog. */
private sealed class Choice(val key: String) {
    class Head(key: String, val text: String) : Choice(key)
    class Pick(
        key: String,
        val label: String,
        val chosen: Boolean,
        val face: Typeface? = null,
        val closes: Boolean = true,
        val remove: (() -> Unit)? = null,
        val onPick: () -> Unit,
    ) : Choice(key)
}

@Composable
private fun DialogTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/** A house press: a black rim, 8 dp corners, at least 48 dp tall. */
@Composable
private fun HousePress(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * − or +: a big press that steps once when pressed and, held, keeps stepping (after 0.4 s, five a
 * second, ten steps at a time after the first dozen), so a long range does not take a hundred presses.
 */
@Composable
private fun StepPress(glyph: String, description: String, onStep: (by: Int) -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 72.dp, height = 64.dp)
            .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { step(1); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    step(1)
                    val held = scope.launch {
                        delay(400)
                        var n = 0
                        while (true) {
                            step(if (n >= 12) 10 else 1)
                            n++
                            delay(200)
                        }
                    }
                    waitForUpOrCancellation()
                    held.cancel()
                }
            },
    ) {
        Text(glyph, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NumberDialog(
    title: String,
    min: Int,
    max: Int,
    current: Int,
    live: Boolean,
    step: Int,
    format: (Int) -> String,
    onValue: (Int) -> Unit,
    close: () -> Unit,
) {
    var value by remember { mutableIntStateOf(current) }
    val unit = step.coerceAtLeast(1)
    fun change(sign: Int, by: Int) {
        val next = IoriDialogs.stepped(value, sign * by, unit, min, max)
        if (next == value) return
        value = next
        if (live) onValue(next)
    }
    EInkDialog(onDismiss = close) {
        DialogTitle(title)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            StepPress("−", stringResource(R.string.cd_decrease)) { change(-1, it) }
            Text(
                text = format(value),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            StepPress("+", stringResource(R.string.cd_increase)) { change(1, it) }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth()) {
            if (live) {
                HousePress(stringResource(R.string.iori_close), Modifier.weight(1f), onClick = close)
            } else {
                HousePress(stringResource(R.string.cancel), Modifier.weight(1f), onClick = close)
                Spacer(Modifier.width(10.dp))
                HousePress(stringResource(R.string.btn_save), Modifier.weight(1f)) { onValue(value); close() }
            }
        }
    }
}

@Composable
private fun ChoiceDialog(title: String, rows: List<Choice>, close: () -> Unit) {
    EInkDialog(onDismiss = close) {
        DialogTitle(title)
        Spacer(Modifier.height(8.dp))
        val drawn = rows.map { row ->
            row.key to @Composable {
                when (row) {
                    is Choice.Head -> Text(
                        row.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    is Choice.Pick -> PickRow(row, close)
                }
            }
        }
        // At most the screen less a margin above and below the dialog, so a long list turns in
        // whole-row pages inside a dialog that still reads as one, not as a new screen.
        val room = LocalConfiguration.current.screenHeightDp.dp - 200.dp
        val state = rememberLazyListState()
        val chosen = rows.indexOfFirst { it is Choice.Pick && it.chosen }
        // The page opens on the ticked row when it is not on the first page.
        LaunchedEffect(Unit) {
            if (chosen <= 0) return@LaunchedEffect
            val info = snapshotFlow { state.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
            val seen = info.visibleItemsInfo.firstOrNull { it.index == chosen }
            if (seen == null || seen.offset + seen.size > info.viewportEndOffset) state.scrollToItem(chosen)
        }
        FittedPages(drawn, Modifier.weight(1f, fill = false).heightIn(max = room.coerceAtLeast(200.dp)), state)
    }
}

@Composable
private fun PickRow(row: Choice.Pick, close: () -> Unit) {
    val family = remember(row.face) { row.face?.let { FontFamily(it) } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { role = Role.RadioButton; selected = row.chosen },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable {
                    row.onPick()
                    if (row.closes) close()
                },
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = family,
                fontWeight = if (row.chosen) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            if (row.chosen) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(24.dp))
            }
        }
        val remove = row.remove
        if (remove != null) {
            Text(
                text = stringResource(R.string.iori_dialog_remove),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(onClick = remove)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
            )
        }
    }
}
