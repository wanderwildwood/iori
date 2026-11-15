package com.wanderwildwood.iori

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.gezimos.inkos.BuildConfig
import com.github.gezimos.inkos.R
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.github.gezimos.inkos.style.Theme
import com.github.gezimos.inkos.style.rememberScreenScale
import com.github.gezimos.inkos.style.scaled
import com.github.gezimos.inkos.ui.compose.HomeUiRenderState

/**
 * Up to three lines from Glance under the home apps, in the quote's font, size and alignment.
 *
 * Nothing scrolls and nothing animates; with no lines it draws nothing at all, except while
 * home is being edited, when one "Home lines" row stands in for them so the dialog can always be
 * reached. A press opens the app a line came from. A long press, or any press while home is
 * being edited, opens the lines dialog, which says what each source is doing. The dialog is
 * also in Settings, beside About Home.
 *
 * After a crash in iori's own code it draws one row saying lines were turned off, and a long
 * press there turns them back on.
 */
@Composable
fun IoriLines(state: HomeUiRenderState, showEditMode: Boolean) {
    if (!IoriLinesPlace.top(LocalContext.current)) Lines(state, showEditMode)
}

/** The same lines under the date line instead, when they are set to the top (Home lines, "Place"). */
@Composable
fun IoriLinesAtTop(state: HomeUiRenderState, showEditMode: Boolean, modifier: Modifier) {
    if (!IoriLinesPlace.top(LocalContext.current)) return
    Column(modifier = modifier.fillMaxWidth()) { Lines(state, showEditMode) }
}

/**
 * Where the home lines are drawn: at the foot, above inkOS's bottom widget (as they come), or
 * at the top under the date line. At the top the home apps move down, so their last row sits at
 * the bottom of the screen, on whatever is still at the foot.
 */
object IoriLinesPlace {
    private const val KEY = "lines_at_top"
    private var atTop by mutableStateOf<Boolean?>(null)

    fun top(context: Context): Boolean =
        atTop ?: Iori.prefs(context).getBoolean(KEY, false).also { atTop = it }

    fun set(context: Context, top: Boolean) {
        Iori.prefs(context).edit().putBoolean(KEY, top).apply()
        atTop = top
    }

    @Composable
    fun appsAlignment(): Alignment = if (top(LocalContext.current)) Alignment.BottomCenter else Alignment.Center

    private var footPx by mutableStateOf(0)

    /** On the column inkOS draws at the foot of home (media, bottom widget, its margin): its height. */
    val footMeasured: Modifier = Modifier.onGloballyPositioned {
        // From its top edge to the bottom of the screen, so the margin under it is counted too.
        val screen = it.parentLayoutCoordinates?.size?.height ?: return@onGloballyPositioned
        footPx = (screen - it.positionInParent().y).toInt().coerceAtLeast(0)
    }

    /** Room left under the apps for what is at the foot. */
    @Composable
    fun appsRoom(): Modifier =
        if (!top(LocalContext.current)) Modifier
        else Modifier.padding(bottom = with(androidx.compose.ui.platform.LocalDensity.current) { footPx.toDp() })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Lines(state: HomeUiRenderState, showEditMode: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tripped by remember { mutableStateOf(Iori.tripped(context)) }
    var dialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tripped = Iori.tripped(context)
                if (!tripped) IoriLinesRepository.refresh(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (BuildConfig.DEBUG && !tripped) homeFault(context)

    val lines by IoriLinesRepository.lines.collectAsState()

    val typeface = remember(state.quoteFont, state.quoteCustomFontPath) {
        try {
            state.quoteFont.getFont(context, state.quoteCustomFontPath)
        } catch (_: Exception) {
            null
        }
    }
    val fontFamily = typeface?.let { FontFamily(it) } ?: FontFamily.Default
    val size = (state.quoteSize * rememberScreenScale()).sp
    val align = when (state.quoteAlignment) {
        0 -> TextAlign.Start
        2 -> TextAlign.End
        else -> TextAlign.Center
    }
    val style = TextStyle(color = Theme.colors.text, fontSize = size, fontFamily = fontFamily, textAlign = align)

    val editing by rememberUpdatedState(showEditMode)
    val openDialog by rememberUpdatedState { dialog = true }

    if (tripped) {
        val turnBackOn by rememberUpdatedState {
            Iori.clearTripped(context)
            tripped = false
            IoriLinesRepository.refresh(context)
        }
        Spacer(Modifier.height(12.dp.scaled(rememberScreenScale())))
        Text(
            text = stringResource(R.string.iori_lines_tripped),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { openDialog() },
                    onLongClick = { turnBackOn() },
                ),
        )
    } else if (lines.isNotEmpty()) {
        Spacer(Modifier.height(12.dp.scaled(rememberScreenScale())))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            lines.forEach { line ->
                val open by rememberUpdatedState { openApp(context, line.pkg) }
                Text(
                    text = buildAnnotatedString {
                        val weight = if (line.bold) FontWeight.Bold else FontWeight.Normal
                        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = weight)) {
                            if (line.lead.isNotEmpty()) {
                                append(line.lead)
                                append("  ")
                            }
                            append(line.text)
                        }
                    },
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (editing) openDialog() else open() },
                            onLongClick = { openDialog() },
                        )
                        .padding(vertical = 2.dp),
                )
            }
        }
    } else if (showEditMode) {
        // No lines to press: Glance missing, too old, refusing, or every source off. The
        // dialog says which, and is where a source switched off here is switched back on.
        Spacer(Modifier.height(12.dp.scaled(rememberScreenScale())))
        Text(
            text = stringResource(R.string.iori_lines_title),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { openDialog() },
                    onLongClick = { openDialog() },
                )
                .padding(vertical = 2.dp),
        )
    }

    if (dialog) {
        LinesDialog(
            tripped = tripped,
            onTurnBackOn = {
                Iori.clearTripped(context)
                tripped = false
                IoriLinesRepository.refresh(context)
            },
            onDismiss = { dialog = false },
        )
    }
}

/** Opens the app a line came from, if it can be opened. A failure leaves home as it was. */
private fun openApp(context: Context, pkg: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

/** Debug builds only: throws from home on demand, to test the crash guard both ways. */
private fun homeFault(context: Context) {
    val fault = try {
        Iori.prefs(context).getString(Iori.KEY_HOME_FAULT, null)
    } catch (_: Exception) {
        null
    } ?: return
    // A fault fires once: cleared before it is thrown, so the next start does not throw again.
    Iori.prefs(context).edit().remove(Iori.KEY_HOME_FAULT).commit()
    when (fault) {
        HomeFault.OWN -> throw IllegalStateException("fault injected in home lines")
        HomeFault.UPSTREAM -> throw IllegalStateException("fault injected upstream").apply {
            stackTrace = arrayOf(
                StackTraceElement("com.github.gezimos.inkos.ui.compose.HomeUIKt", "HomeUI", "HomeUI.kt", 1),
            )
        }
    }
}
