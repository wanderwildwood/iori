package com.wanderwildwood.iori.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.style.Theme
import kotlin.math.max

/**
 * Whole-row pages for inkOS's Settings pages (decided 11-19: a page turn never cuts a row).
 *
 * inkOS lays a Settings page out as one column of composables, so [PagedColumn], which pages by
 * list items, saw a single tall item and turned it a screen at a time, cutting rows in half. Here
 * each row reports where it sits ([settingsRow], hooked into SettingsComposable's rows), and the
 * page follows the house list's rules (STYLE.md, Lists; [PagedColumn]):
 *
 * - a turn forward brings the first row that did not fit to the top of the page;
 * - a row that does not fit at the foot is neither drawn nor pressable until the next page;
 * - a heading is never the last thing on a page: it goes over with the row under it;
 * - a turn back goes one page back and lands on a row;
 * - a row taller than the page is read a page at a time within it, as [PagedColumn] does, and
 *   is not cut at its top when only its heading would be left above it (found 11-19: such a row
 *   left a page with its heading alone, and the turn after it skipped the rest of the row);
 * - the scrollbar is drawn as [PagedColumn] draws it, its thumb measured in pixels.
 *
 * Content that is not a reported row (a picture, a note) is turned past a page at a time.
 */
object IoriSettingsPages {

    /** A row's span down the page's content, in px; [heading] when it heads a group. */
    data class Span(val top: Int, val bottom: Int, val heading: Boolean = false, val key: Any? = null)

    /**
     * The headings with nothing under them: what follows is another heading, or nothing. Rows
     * that cannot be used are left out (SettingsComposable), so a group can be emptied; its
     * heading folds to no height rather than stand alone. A folded heading keeps its place, so
     * whether it folds never depends on itself, only on what follows, and it opens again when
     * its rows come back.
     */
    fun emptyHeadings(spans: List<Span>): Set<Any> {
        val out = HashSet<Any>()
        spans.filter { it.heading && it.key != null }.forEach { h ->
            val next = spans.filter { it !== h && it.top >= h.bottom }
                .minWithOrNull(compareBy<Span>({ it.top }, { if (it.heading) 0 else 1 }))
            if (next == null || next.heading) out += h.key!!
        }
        return out
    }

    /** Where a page that opens at [top] stops drawing, or null to draw to the foot. */
    fun foot(spans: List<Span>, top: Int, height: Int): Int? {
        val sorted = spans.sortedBy { it.top }
        val cut = sorted.firstOrNull { it.bottom > top + height && it.top > top } ?: return null
        // A row taller than the page with nothing but headings above it on this page: a turn
        // would not bring it whole either, so it is drawn as far as it goes.
        if (cut.bottom - cut.top > height && sorted.none { !it.heading && it.top >= top && it.top < cut.top }) return null
        var foot = cut.top
        // A heading left alone at the foot goes over with its rows.
        val before = sorted.lastOrNull { it.bottom <= foot && it.top >= top }
        if (before != null && before.heading && before.top > top) foot = before.top
        return foot - top
    }

    /**
     * Where the content ends: the foot of the last row. A page's content can close on a spacer
     * that is not a row (16dp on Settings, 24dp on Gestures and Fonts); measured to the spacer, a
     * page whose rows all fit still had more to show, and a turn forward brought nothing new.
     * With no rows, the whole content.
     */
    fun end(spans: List<Span>, contentHeight: Int): Int = spans.maxOfOrNull { it.bottom } ?: contentHeight

    /** Where a turn forward from a page opening at [top] lands, or null at the end. */
    fun forward(spans: List<Span>, top: Int, height: Int, contentHeight: Int): Int? {
        if (top + height >= end(spans, contentHeight)) return null
        val f = foot(spans, top, height)
        // Nothing is cut inside the page, or the first cut lies past its foot: what runs past the
        // foot is a row taller than the page (or content that is not a row), so the next page is
        // the next part of it, never the row after it.
        if (f == null || f > height) return (top + height).coerceAtMost(lastStart(spans, height, contentHeight))
        return top + f
    }

    /** Where a turn back from a page opening at [top] lands, or null at the start. */
    fun back(spans: List<Span>, top: Int, height: Int): Int? {
        if (top <= 0) return null
        val target = (top - height).coerceAtLeast(0)
        if (target == 0) return 0
        // The first row that starts on or after the target, so the page opens on a row's edge.
        return spans.map { it.top }.filter { it in target until top }.minOrNull() ?: target
    }

    /** The page a jump to [fraction] of the way down opens on: the row starting nearest above it. */
    fun jump(spans: List<Span>, fraction: Float, height: Int, contentHeight: Int): Int {
        val target = (fraction * end(spans, contentHeight)).toInt().coerceIn(0, lastStart(spans, height, contentHeight))
        return spans.map { it.top }.filter { it <= target }.maxOrNull() ?: 0
    }

    /** The page that shows the end: it opens on the first row that lets the last one fit. */
    fun lastStart(spans: List<Span>, height: Int, contentHeight: Int): Int {
        val least = (end(spans, contentHeight) - height).coerceAtLeast(0)
        return spans.map { it.top }.filter { it >= least }.minOrNull() ?: least
    }

    private class Registry {
        var content: LayoutCoordinates? = null
        val rows = HashMap<Any, Pair<LayoutCoordinates, Boolean>>()
        /** Headings folded because nothing follows them. */
        val folded = mutableStateMapOf<Any, Boolean>()
        /** Set by [Pages]: read the rows again (a row moved, came or went). */
        var onChange: () -> Unit = {}
        fun spans(): List<Span> {
            val c = content ?: return emptyList()
            if (!c.isAttached) return emptyList()
            return rows.entries.mapNotNull { (key, value) ->
                val (r, heading) = value
                if (!r.isAttached) return@mapNotNull null
                val y = c.localPositionOf(r, Offset.Zero).y.toInt()
                Span(y, y + r.size.height, heading, key)
            }
        }
    }

    private val LocalRegistry = compositionLocalOf<Registry?> { null }

    /** Marks a Settings row (or, with [heading], a group's heading) for whole-row pages. */
    fun Modifier.settingsRow(heading: Boolean = false): Modifier = composed {
        val registry = LocalRegistry.current ?: return@composed this
        val key = remember { Any() }
        androidx.compose.runtime.DisposableEffect(registry) {
            onDispose { registry.rows.remove(key); registry.folded.remove(key); registry.onChange() }
        }
        val fold = heading && registry.folded[key] == true
        this
            .onGloballyPositioned { registry.rows[key] = it to heading; registry.onChange() }
            .then(if (fold) Modifier.layout { m, c -> val p = m.measure(c); layout(p.width, 0) {} } else Modifier)
    }

    /**
     * Where each Settings page stood when a sub-page opened over it, so Back returns to that page
     * of it and not to its first (found in a phone test 11-19). Settings calls [push] as it opens a
     * sub-page and [pop] as it goes back; [Pages] keeps [top] current and opens at [opening].
     */
    class PageMemory {
        internal var top = 0
        internal var opening = 0
        private val stack = ArrayList<Int>()
        fun push() { stack.add(top); opening = 0 }
        fun pop() { opening = stack.removeLastOrNull() ?: 0 }
    }

    /**
     * A Settings page; [resetKey] changes when the page does, and the new page opens at its top,
     * or, with [memory], where it stood when the page was left for a sub-page.
     */
    @Composable
    fun Pages(resetKey: Any, memory: PageMemory? = null, content: @Composable () -> Unit) {
        val registry = remember { Registry() }
        var top by remember { mutableIntStateOf(0) }
        var height by remember { mutableIntStateOf(0) }
        var contentHeight by remember { mutableIntStateOf(0) }
        var spans by remember { mutableStateOf(emptyList<Span>()) }
        LaunchedEffect(resetKey) { top = memory?.opening ?: 0 }
        androidx.compose.runtime.SideEffect { memory?.top = top }
        fun refresh() {
            spans = registry.spans()
            val empty = emptyHeadings(spans)
            spans.filter { it.heading }.forEach { h ->
                val fold = h.key in empty
                if ((registry.folded[h.key!!] == true) != fold) registry.folded[h.key] = fold
            }
        }
        registry.onChange = { refresh() }
        fun turnForward() { refresh(); forward(spans, top, height, contentHeight)?.let { top = it } }
        fun turnBack() { refresh(); back(spans, top, height)?.let { top = it } }
        val ink = Theme.colors.text
        val paper = Theme.colors.background
        val foot = foot(spans, top, height)
        val end = end(spans, contentHeight)
        val many = end > height && height > 0

        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .onSizeChanged { height = it.height }
                    .pointerInput(Unit) {
                        var turned = false
                        detectVerticalDragGestures(onDragStart = { turned = false }) { _, drag ->
                            if (!turned && drag != 0f) {
                                turned = true
                                if (drag < 0f) turnForward() else turnBack()
                            }
                        }
                    }
                    // A row hidden at the foot takes no press: what cannot be seen is not there.
                    .pointerInput(foot) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (foot != null && down.position.y >= foot) down.consume()
                        }
                    }
                    .drawWithContent {
                        if (foot == null) drawContent() else clipRect(bottom = foot.toFloat()) { this@drawWithContent.drawContent() }
                    },
            ) {
                androidx.compose.runtime.CompositionLocalProvider(LocalRegistry provides registry) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                                contentHeight = placeable.height
                                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -top) }
                            }
                            .onGloballyPositioned { registry.content = it; refresh() },
                    ) { content() }
                }
            }
            if (many) Bar(
                top = top, height = height, contentHeight = end,
                atStart = top <= 0, atEnd = top + height >= end,
                ink = ink, paper = paper,
                onBack = { turnBack() },
                onForward = { turnForward() },
                onFirst = { top = 0 },
                onLast = { refresh(); top = lastStart(spans, height, contentHeight) },
                onJump = { f -> refresh(); top = jump(spans, f, height, contentHeight) },
            )
        }
    }

    /** [PagedColumn]'s bar (MMD's `VerticalScrollbar` as MMD draws it), measured in pixels. */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Bar(
        top: Int, height: Int, contentHeight: Int, atStart: Boolean, atEnd: Boolean,
        ink: androidx.compose.ui.graphics.Color, paper: androidx.compose.ui.graphics.Color,
        onBack: () -> Unit, onForward: () -> Unit, onFirst: () -> Unit, onLast: () -> Unit, onJump: (Float) -> Unit,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(start = 8.dp, end = 8.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = if (atStart) rememberVectorPainter(OutlineUp) else painterResource(R.drawable.iori_chevron_filled_up),
                contentDescription = "Previous page",
                tint = ink,
                modifier = Modifier.padding(vertical = 16.dp).size(24.dp).combinedClickable(onClick = onBack, onLongClick = onFirst),
            )
            Canvas(
                modifier = Modifier
                    .width(8.dp)
                    .weight(1f)
                    .border(width = 1.dp, color = ink, shape = RoundedCornerShape(4.dp))
                    .pointerInput(Unit) { detectTapGestures { offset -> onJump(offset.y / size.height) } },
            ) {
                val slider = (size.height * height / contentHeight.coerceAtLeast(1)).coerceAtLeast(max(size.height * 0.05f, 16f))
                val maxOffset = (size.height - slider).coerceAtLeast(0f)
                val offset = (top.toFloat() / (contentHeight - height).coerceAtLeast(1) * maxOffset).coerceIn(0f, maxOffset)
                val corner = CornerRadius(size.width / 2, size.width / 2)
                drawRoundRect(color = paper, topLeft = Offset.Zero, size = Size(size.width, size.height), cornerRadius = corner)
                drawRoundRect(color = ink, topLeft = Offset(0f, offset), size = Size(size.width, slider), cornerRadius = corner)
            }
            Icon(
                painter = if (atEnd) rememberVectorPainter(OutlineDown) else painterResource(R.drawable.iori_chevron_filled_down),
                contentDescription = "Next page",
                tint = ink,
                modifier = Modifier.padding(vertical = 16.dp).size(24.dp).combinedClickable(onClick = onForward, onLongClick = onLast),
            )
        }
    }

    // The end arrows as the shared PagedColumn draws them (15 Nov 2026): the filled chevron's
    // shape as an outline in the ink color; a dotted one came out as a grey dither on e-ink.
    private val OutlineUp: ImageVector = outline(
        "ChevronOutlineUp",
        "M13.347,6.001C12.621,5.215 11.379,5.215 10.653,6.001L3.84,13.382C2.756,14.556 3.589,16.458 5.187,16.458H18.813C20.411,16.458 21.244,14.556 20.16,13.382L13.347,6.001Z",
    )
    private val OutlineDown: ImageVector = outline(
        "ChevronOutlineDown",
        "M10.653,17.999C11.379,18.785 12.621,18.785 13.347,17.999L20.16,10.618C21.244,9.444 20.411,7.542 18.813,7.542L5.187,7.542C3.589,7.542 2.756,9.444 3.84,10.618L10.653,17.999Z",
    )

    private fun outline(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 1.5f)
            .build()
}
