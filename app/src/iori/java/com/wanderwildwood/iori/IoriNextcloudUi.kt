package com.wanderwildwood.iori

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.helper.ShapeHelper
import com.github.gezimos.inkos.style.SettingsTheme
import com.github.gezimos.inkos.style.Theme
import com.github.gezimos.inkos.style.rememberScreenScale
import com.github.gezimos.inkos.style.scaled
import com.github.gezimos.inkos.ui.compose.SettingsComposable.SettingsHomeItem
import com.github.gezimos.inkos.ui.compose.inkOsSafeDrawingPadding
import com.wanderwildwood.iori.IoriNextcloud.Phase
import com.wanderwildwood.iori.IoriNextcloud.Problem
import com.wanderwildwood.iori.ui.EInkDialog
import com.wanderwildwood.iori.ui.LocalListBleed
import com.wanderwildwood.iori.ui.PagedColumn
import com.wanderwildwood.iori.ui.houseTypography
import com.wanderwildwood.iori.ui.monochrome

/**
 * The phone's Nextcloud account as the person sees it ([IoriAccount] keeps it, [IoriNextcloud]
 * signs in and out): a page of the first run, after Default apps, drawn as that page is, and a
 * row in Settings that opens the same thing in one of Home's own dialogs.
 *
 * Signed out, it asks for the server's address and opens the server's own sign-in page in the
 * browser; on a phone with no browser, or when the person would rather, it takes a user name and
 * an app password made on the server. Signed in, it says who, and Sign out asks in its own row.
 */
object IoriNextcloudUi {

    /** How the same rows are drawn in the two places. */
    private class Look(
        val label: TextStyle,
        val note: TextStyle,
        val ink: Color,
        val paper: Color,
        val shape: Shape,
        /** A dialog's buttons run its full width; a first-run page's are as wide as their words. */
        val wide: Boolean,
    )

    @Composable
    private fun Button(text: String, look: Look, enabled: Boolean = true, onClick: () -> Unit) {
        Text(
            text = text,
            style = look.label,
            color = look.ink,
            textAlign = if (look.wide) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .padding(vertical = 5.dp)
                .then(if (look.wide) Modifier.fillMaxWidth() else Modifier)
                .border(1.5.dp, look.ink, look.shape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }

    @Composable
    private fun Field(
        name: String,
        value: String,
        look: Look,
        hint: String = "",
        keyboard: KeyboardType = KeyboardType.Text,
        hidden: Boolean = false,
        last: Boolean = false,
        onChange: (String) -> Unit,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(name, style = look.note, color = look.ink)
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.replace("\n", "")) },
                singleLine = true,
                textStyle = look.label.copy(color = look.ink),
                cursorBrush = SolidColor(look.ink),
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboard,
                    autoCorrectEnabled = false,
                    imeAction = if (last) ImeAction.Done else ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, look.ink, look.shape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        // The hint is an outline of what goes here, said in the same ink: no grey on this panel.
                        if (value.isEmpty() && hint.isNotEmpty()) Text(hint, style = look.label, color = look.ink.copy(alpha = 0.45f))
                        inner()
                    }
                },
            )
        }
    }

    private fun words(problem: Problem): Int = when (problem) {
        Problem.ADDRESS -> R.string.iori_nc_bad_address
        Problem.NEEDS_HTTPS -> R.string.iori_nc_needs_https
        Problem.UNREACHABLE -> R.string.iori_nc_unreachable
        Problem.NOT_NEXTCLOUD -> R.string.iori_nc_not_nextcloud
        Problem.WRONG_PASSWORD -> R.string.iori_nc_wrong_password
        Problem.NO_BROWSER -> R.string.iori_nc_no_browser
        Problem.NOT_KEPT -> R.string.iori_nc_not_kept
        Problem.GAVE_UP -> R.string.iori_nc_gave_up
        Problem.NO_APP_PASSWORD -> R.string.iori_nc_no_app_password
    }

    /**
     * The rows themselves, the same in the page and in the dialog. [fitted]: the list is as tall as
     * its rows and no taller (the dialog), not the whole room it is given (the first-run page).
     */
    @Composable
    private fun Rows(look: Look, modifier: Modifier, fitted: Boolean = false) {
        val context = LocalContext.current
        LaunchedEffect(Unit) { IoriNextcloud.load(context) }
        OnResume { IoriNextcloud.load(context) }
        val shown = IoriNextcloud.shown
        val phase = IoriNextcloud.phase

        var address by rememberSaveable { mutableStateOf("") }
        var user by rememberSaveable { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var reveal by remember { mutableStateOf(false) }
        var typing by rememberSaveable { mutableStateOf(false) }
        // With no browser there is nothing to open the server's page with: the typed way is the way.
        LaunchedEffect(phase) { if (phase is Phase.Failed && phase.problem == Problem.NO_BROWSER) typing = true }

        // Sign out asks in its own row: armed by one press, done by the next, and disarmed by a
        // press anywhere else. Every press on these rows is counted, so "the next" can be told.
        var presses by remember { mutableIntStateOf(0) }
        var armedAt by remember { mutableIntStateOf(-1) }
        val armed = armedAt == presses

        val busy = phase is Phase.Asking || phase is Phase.Waiting || phase is Phase.Checking
        val browser = remember { IoriNextcloud.hasBrowser(context) }

        val counted = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) presses++
                }
            }
        }
        val rows = mutableListOf<Pair<String, @Composable () -> Unit>>()
        fun item(key: String, row: @Composable () -> Unit) { rows += key to row }
        run {
            if (shown != null) {
                item(key = "who") {
                    Text(
                        stringResource(R.string.iori_nc_signed_in, shown.user, shown.host),
                        style = look.label, color = look.ink, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                item(key = "shared") {
                    Text(stringResource(R.string.iori_nc_shared), style = look.note, color = look.ink, modifier = Modifier.padding(vertical = 6.dp))
                }
                item(key = "out") {
                    Button(stringResource(if (armed) R.string.iori_nc_sign_out_armed else R.string.iori_nc_sign_out), look) {
                        // This press was counted too: armed one press ago means it is the second on this row.
                        if (armedAt == presses - 1) {
                            armedAt = -1
                            password = ""
                            IoriNextcloud.signOut(context)
                        } else {
                            armedAt = presses
                        }
                    }
                }
                return@run
            }
            item(key = "server") {
                Field(
                    stringResource(R.string.iori_nc_server), address, look,
                    hint = stringResource(R.string.iori_nc_server_hint), keyboard = KeyboardType.Uri, last = !typing,
                ) {
                    address = it.trim()
                    if (phase is Phase.Failed) IoriNextcloud.stop()
                }
            }
            if (typing) {
                item(key = "user") {
                    Field(stringResource(R.string.iori_nc_user), user, look, keyboard = KeyboardType.Email) {
                        user = it
                        if (phase is Phase.Failed) IoriNextcloud.stop()
                    }
                }
                item(key = "password") {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) {
                            Field(
                                stringResource(R.string.iori_nc_password), password, look,
                                keyboard = KeyboardType.Password, hidden = !reveal, last = true,
                            ) {
                                password = it
                                if (phase is Phase.Failed) IoriNextcloud.stop()
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (reveal) R.string.iori_nc_hide else R.string.iori_nc_show),
                            style = look.note, color = look.ink,
                            modifier = Modifier.clickable { reveal = !reveal }.padding(horizontal = 8.dp, vertical = 16.dp),
                        )
                    }
                }
                item(key = "password_note") {
                    // In bold, as the status is: what becomes of a login password typed here is said plainly.
                    Text(
                        stringResource(R.string.iori_nc_password_note),
                        style = look.note, color = look.ink, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
            val status = when (phase) {
                is Phase.Asking -> R.string.iori_nc_asking
                is Phase.Waiting -> R.string.iori_nc_waiting
                is Phase.Checking -> R.string.iori_nc_checking
                is Phase.Failed -> words(phase.problem)
                else -> null
            }
            if (status != null) item(key = "status") {
                Text(stringResource(status), style = look.note, color = look.ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
            }
            if (busy) {
                item(key = "stop") { Button(stringResource(R.string.iori_nc_stop), look) { IoriNextcloud.stop() } }
            } else {
                item(key = "go") {
                    Button(stringResource(R.string.iori_nc_sign_in), look) {
                        when {
                            address.isBlank() -> Unit
                            !typing -> IoriNextcloud.signInWithBrowser(context, address)
                            user.isNotBlank() && password.isNotEmpty() -> IoriNextcloud.signInTyped(context, address, user, password)
                        }
                    }
                }
                if (!typing) item(key = "type") {
                    Button(stringResource(R.string.iori_nc_type), look) {
                        typing = true
                        IoriNextcloud.stop()
                    }
                } else if (browser) item(key = "browser") {
                    Button(stringResource(R.string.iori_nc_browser), look) {
                        typing = false
                        IoriNextcloud.stop()
                    }
                }
            }
        }
        if (fitted) {
            // At most the screen less a margin above and below, as the choice dialogs are.
            val room = LocalConfiguration.current.screenHeightDp.dp - 200.dp
            FittedPages(rows, counted.heightIn(max = room.coerceAtLeast(200.dp)))
        } else {
            PagedColumn(counted) { rows.forEach { (key, row) -> item(key = key) { row() } } }
        }
    }

    /** The Nextcloud page of a first run ([IoriFirstRun]), after Default apps, drawn as that page is. */
    @Composable
    fun Page(pageLabel: String, isDark: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        val sizes = remember { IoriFirstRun.sizes(context, prefs) }
        SettingsTheme(isDark = isDark) {
            val textColor = Theme.colors.text
            val bgColor = Theme.colors.background
            val screenScale = rememberScreenScale()
            val shape = remember(prefs.textIslandsShape) {
                ShapeHelper.getRoundedCornerShape(textIslandsShape = prefs.textIslandsShape, pillRadius = 16.dp)
            }
            BackHandler(enabled = true) { onBack() }
            Column(Modifier.fillMaxSize().background(bgColor).inkOsSafeDrawingPadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 32.dp.scaled(screenScale))
                        .padding(top = 32.dp.scaled(screenScale)),
                ) {
                    Text(
                        text = stringResource(R.string.iori_first_nextcloud),
                        style = SettingsTheme.typography.title,
                        fontSize = sizes.big,
                        fontWeight = FontWeight.Black,
                        color = textColor,
                        lineHeight = (sizes.big.value * 1.05f).sp,
                    )
                    Spacer(Modifier.height(16.dp.scaled(screenScale)))
                    Box(Modifier.width(60.dp.scaled(screenScale)).height(5.dp).background(textColor))
                    Spacer(Modifier.height(16.dp.scaled(screenScale)))
                    // The invitation is for someone not signed in; signed in, the rows say what there is to say.
                    if (IoriNextcloud.shown == null) {
                        Text(
                            text = stringResource(R.string.iori_nc_body) + " " + stringResource(R.string.iori_nc_skip),
                            style = SettingsTheme.typography.body,
                            fontSize = sizes.body,
                            color = textColor,
                            lineHeight = (sizes.body.value * 1.4f).sp,
                        )
                        Spacer(Modifier.height(8.dp.scaled(screenScale)))
                    }
                    val body = SettingsTheme.typography.body.copy(color = textColor)
                    val look = Look(body.copy(fontSize = sizes.title), body.copy(fontSize = sizes.body), textColor, bgColor, shape, wide = false)
                    MaterialTheme(colorScheme = monochrome.copy(primary = textColor, onPrimary = bgColor)) {
                        CompositionLocalProvider(LocalListBleed provides 26.dp.scaled(screenScale), LocalContentColor provides textColor) {
                            Rows(look, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
                with(IoriFirstRun) {
                    Footer(
                        left = stringResource(R.string.btn_back_upper),
                        middle = pageLabel,
                        right = stringResource(R.string.btn_next),
                        fontSize = sizes.title,
                        textColor = textColor,
                        onLeft = onBack,
                        onRight = onNext,
                    )
                }
            }
        }
    }

    /** The row in Settings, which says who is signed in, and its dialog. */
    @Composable
    fun SettingsRow(titleFontSize: TextUnit) {
        val context = LocalContext.current
        var open by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { IoriNextcloud.load(context) }
        val shown = IoriNextcloud.shown
        SettingsHomeItem(
            title = stringResource(R.string.iori_nc_title),
            imageVector = Icons.Rounded.Cloud,
            description = if (shown == null) stringResource(R.string.iori_nc_row_out) else stringResource(R.string.iori_nc_row_in, shown.user, shown.host),
            titleFontSize = titleFontSize,
            onClick = { open = true },
        )
        if (!open) return
        MaterialTheme(colorScheme = monochrome, typography = houseTypography) {
            EInkDialog(onDismiss = { open = false }) {
                Text(stringResource(R.string.iori_nc_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (shown == null) {
                    Text(stringResource(R.string.iori_nc_body), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                }
                val look = Look(
                    MaterialTheme.typography.bodyMedium, MaterialTheme.typography.bodySmall,
                    Color.Black, Color.White, RoundedCornerShape(0.dp), wide = true,
                )
                Rows(look, Modifier.fillMaxWidth().weight(1f, fill = false), fitted = true) // as tall as its rows, not the whole screen
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.iori_close),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Black)
                        .clickable { open = false }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}
