package com.wanderwildwood.iori

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavController
import com.github.gezimos.inkos.MainViewModel
import com.github.gezimos.inkos.R
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Prefs
import com.github.gezimos.inkos.helper.ShapeHelper
import com.github.gezimos.inkos.helper.device.DeviceHelper
import com.github.gezimos.inkos.helper.isinkosDefault
import com.github.gezimos.inkos.style.SettingsTheme
import com.github.gezimos.inkos.style.Theme
import com.github.gezimos.inkos.style.UiScaleMode
import com.github.gezimos.inkos.style.detectScaleMode
import com.github.gezimos.inkos.style.rememberScreenScale
import com.github.gezimos.inkos.style.scaled
import com.github.gezimos.inkos.ui.compose.ThemePresetPicker
import com.github.gezimos.inkos.ui.compose.inkOsSafeDrawingPadding
import com.github.gezimos.inkos.ui.dialogs.ComposeDialogManager
import kotlinx.coroutines.delay
import kotlin.reflect.KProperty

/**
 * Home's first run, six pages in place of inkOS's seven, drawn the way inkOS draws its own:
 *
 *  1. Welcome: what Home is, what it sends, and the privacy tick.
 *  2. Setup: notification access, the home app, and where Settings is (with its tick).
 *  3. Ecosystem: the apps Home works with, to tick and get ([IoriPickApps]).
 *  4. Default apps: which app Android opens for alarms, pictures, maps and the like ([IoriDefaults]).
 *  5. Nextcloud: the one account the apps can share ([IoriNextcloudUi]); NEXT passes it by.
 *  6. Theme and home apps, last, so the apps just got can be put on the home screen.
 *
 * inkOS's own `OnboardingScreen.Show` is left as upstream wrote it and is not called.
 */
object IoriFirstRun {

    const val PAGES = 6
    private const val WELCOME = 0
    private const val SETUP = 1
    private const val ECOSYSTEM = 2
    private const val DEFAULTS = 3
    private const val NEXTCLOUD = 4
    private const val LOOK = 5

    /** The sizes inkOS's first-run pages work out for themselves. */
    internal class Sizes(val title: TextUnit, val body: TextUnit, val big: TextUnit)

    internal fun sizes(context: Context, prefs: Prefs): Sizes {
        val scale = detectScaleMode(context).let { mode ->
            if (prefs.uiScaleMode != 0) UiScaleMode.fromId(prefs.uiScaleMode).scale else mode.scale
        }
        val size = prefs.settingsSize - 3
        return Sizes((size * 1.5f * scale).sp, (size * 1.2f * scale).sp, (size * 3f * scale).sp)
    }

    private fun nightMode(mode: Constants.Theme) = when (mode) {
        Constants.Theme.Light -> AppCompatDelegate.MODE_NIGHT_NO
        Constants.Theme.Dark -> AppCompatDelegate.MODE_NIGHT_YES
        Constants.Theme.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /**
     * Whether MainActivity should open the first run now. Not when the activity is made again
     * with its pages still in the back stack (a font, size or language change in Android's
     * settings, or the process ended while the person was away): a second copy would be put on
     * top, and FINISH would then land on the first page of the one underneath.
     */
    fun notShown(navController: NavController, savedInstanceState: android.os.Bundle?): Boolean =
        savedInstanceState == null ||
            runCatching { navController.getBackStackEntry(R.id.onboardingFragment) }.isFailure

    /**
     * Called by [Iori.Guard] when iori's own code crashed. During a first run the page that was
     * up is passed by on the next start, and a crash on the last page ends the first run, so one
     * broken page cannot bring the launcher back to itself for ever. Run setup again in
     * Settings gives the pages another go. Written with commit: the process is about to end.
     */
    internal fun tripped(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.firstOpen) return
        val crashed = prefs.onboardingPage.coerceIn(0, PAGES - 1)
        if (crashed >= PAGES - 1) {
            prefs.onboardingPage = 0
            prefs.commitFirstOpen(false)
        } else {
            prefs.onboardingPage = crashed + 1
            prefs.commitFirstOpen(true)
        }
    }

    /** The page in hand, saved the moment it changes, before the new page is drawn (see [tripped]). */
    private class SavedPage(private val prefs: Prefs) {
        private val state = mutableIntStateOf(prefs.onboardingPage.coerceIn(0, PAGES - 1))
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = state.intValue
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.intValue = value
            prefs.onboardingPage = value
        }
    }

    private fun notificationAccess(context: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    @Composable
    fun Show(onFinish: () -> Unit, onEditFavorites: () -> Unit) {
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        val activity = LocalActivity.current
        val viewModel = (activity as? ViewModelStoreOwner)?.let { ViewModelProvider(it)[MainViewModel::class.java] }
        val sizes = sizes(context, prefs)

        val systemDark = isSystemInDarkTheme()
        var themeMode by remember { mutableStateOf(prefs.appTheme) }
        val isDark = when (themeMode) {
            Constants.Theme.Dark -> true
            Constants.Theme.Light -> false
            Constants.Theme.System -> systemDark
        }
        // A page number kept from inkOS's seven pages lands on the last of these.
        val savedPage = remember { SavedPage(prefs) }
        var page by savedPage
        // Run setup again in Settings: agreed to before, and left again by BACK on the first page.
        val rerun = remember { !prefs.firstOpen }

        var showThemePicker by remember { mutableStateOf(false) }
        var privacyAgreed by remember { mutableStateOf(rerun || page > WELCOME) }
        var settingsRead by remember { mutableStateOf(rerun || page > SETUP) }
        var privacyToastShown by remember { mutableStateOf(false) }
        var settingsToastShown by remember { mutableStateOf(false) }

        // Both are granted on Android's own screens; looked at again each second while Setup is up.
        var hasAccess by remember { mutableStateOf(notificationAccess(context)) }
        var isDefault by remember { mutableStateOf(isinkosDefault(context)) }
        LaunchedEffect(page) {
            while (page == SETUP && !(hasAccess && isDefault)) {
                delay(1000)
                hasAccess = notificationAccess(context)
                isDefault = isinkosDefault(context)
            }
        }

        fun setTheme(mode: Constants.Theme) {
            themeMode = mode
            viewModel?.setAppTheme(mode)
            AppCompatDelegate.setDefaultNightMode(nightMode(mode))
        }

        fun back() {
            if (page == LOOK && showThemePicker) showThemePicker = false
            else if (page > 0) page--
            else if (rerun) onFinish()
        }

        if (page == ECOSYSTEM) {
            IoriPickApps.Page(
                pageLabel = "${ECOSYSTEM + 1}/$PAGES",
                isDark = isDark,
                onBack = { page = SETUP },
                onNext = { page = DEFAULTS },
            )
            return
        }
        if (page == DEFAULTS) {
            IoriDefaults.Page(
                pageLabel = "${DEFAULTS + 1}/$PAGES",
                isDark = isDark,
                onBack = { page = ECOSYSTEM },
                onNext = { page = NEXTCLOUD },
            )
            return
        }
        if (page == NEXTCLOUD) {
            IoriNextcloudUi.Page(
                pageLabel = "${NEXTCLOUD + 1}/$PAGES",
                isDark = isDark,
                onBack = { page = DEFAULTS },
                onNext = { page = LOOK },
            )
            return
        }

        SettingsTheme(isDark = isDark) {
            val textColor = Theme.colors.text
            val bgColor = Theme.colors.background
            val screenScale = rememberScreenScale()
            val buttonShape = remember(prefs.textIslandsShape) {
                ShapeHelper.getRoundedCornerShape(textIslandsShape = prefs.textIslandsShape, pillRadius = 16.dp)
            }
            fun Dp.s() = scaled(screenScale)

            // On the first page of a first run the back key is swallowed: a first run is not left
            // by it. Run setup again is left by it, back to Settings.
            BackHandler(enabled = true) { back() }

            var einkMode by remember { mutableStateOf(DeviceHelper.isEinkDevice()) }
            val themePickerIndex = remember { mutableIntStateOf(0) }
            val themeSelectAction = remember { mutableStateOf<(() -> Unit)?>(null) }

            Column(Modifier.fillMaxSize().background(bgColor).inkOsSafeDrawingPadding()) {
                if (page == LOOK && showThemePicker && viewModel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp.s()).padding(top = 32.dp.s()),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_theme),
                            style = SettingsTheme.typography.title,
                            fontSize = sizes.big,
                            fontWeight = FontWeight.Black,
                            color = textColor,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(IntrinsicSize.Min),
                        ) {
                            Text(
                                text = stringResource(R.string.iori_first_mono),
                                style = SettingsTheme.typography.title,
                                fontSize = sizes.title,
                                color = if (einkMode) bgColor else textColor,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clip(buttonShape)
                                    .background(if (einkMode) textColor else bgColor)
                                    .then(if (!einkMode) Modifier.border(2.dp, textColor, buttonShape) else Modifier)
                                    .clickable { einkMode = !einkMode }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            // Light, dark or the system's: not asked where the system's own dark setting decides.
                            if (!remember { IoriDark.systemHasDarkSetting(context) }) Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(buttonShape)
                                    .border(2.dp, textColor, buttonShape)
                                    .clickable {
                                        setTheme(
                                            when (themeMode) {
                                                Constants.Theme.Light -> Constants.Theme.Dark
                                                Constants.Theme.Dark -> Constants.Theme.System
                                                Constants.Theme.System -> Constants.Theme.Light
                                            }
                                        )
                                    }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = when (themeMode) {
                                        Constants.Theme.Light -> Icons.Rounded.LightMode
                                        Constants.Theme.Dark -> Icons.Rounded.DarkMode
                                        Constants.Theme.System -> Icons.Rounded.Android
                                    },
                                    contentDescription = stringResource(R.string.iori_first_theme_toggle, stringResource(when (themeMode) { Constants.Theme.Light -> R.string.light; Constants.Theme.Dark -> R.string.dark; Constants.Theme.System -> R.string.system_default })),
                                    tint = textColor,
                                    modifier = Modifier.size(20.dp.s()),
                                )
                            }
                        }
                    }
                    ThemePresetPicker(
                        isDark = isDark,
                        themeMode = themeMode,
                        onThemeModeChange = { setTheme(it) },
                        viewModel = viewModel,
                        prefs = prefs,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        applyOnFirstComposition = false,
                        titleFontSize = sizes.title,
                        showThemeModeToggleUnderCards = false,
                        requireConfirmation = true,
                        applyFontImmediately = true,
                        einkMode = einkMode,
                        currentIndexState = themePickerIndex,
                        selectAction = themeSelectAction,
                    )
                } else {
                    // The words scroll at any text size; the tick under them stays on screen.
                    val scroll = remember(page) { ScrollState(0) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scroll)
                            .padding(horizontal = 32.dp.s())
                            .padding(top = 32.dp.s(), bottom = 16.dp.s()),
                    ) {
                        Text(
                            text = stringResource(
                                when (page) {
                                    WELCOME -> R.string.onboarding_welcome
                                    SETUP -> R.string.iori_first_setup
                                    else -> R.string.iori_first_look
                                }
                            ),
                            style = SettingsTheme.typography.title,
                            fontSize = sizes.big,
                            fontWeight = FontWeight.Black,
                            color = textColor,
                            lineHeight = (sizes.big.value * 1.05f).sp,
                        )
                        Spacer(Modifier.height(16.dp.s()))
                        Box(Modifier.width(60.dp.s()).height(5.dp).background(textColor))
                        Spacer(Modifier.height(20.dp.s()))

                        @Composable
                        fun Body(text: Int) = Text(
                            text = stringResource(text),
                            style = SettingsTheme.typography.body,
                            fontSize = sizes.body,
                            lineHeight = (sizes.body.value * 1.4f).sp,
                            color = textColor,
                        )

                        @Composable
                        fun Button(text: Int, onClick: () -> Unit) = Text(
                            text = stringResource(text),
                            style = SettingsTheme.typography.title,
                            fontSize = sizes.title,
                            color = textColor,
                            modifier = Modifier
                                .clip(buttonShape)
                                .border(1.5.dp.s(), textColor, buttonShape)
                                .clickable(onClick = onClick)
                                .padding(horizontal = 24.dp.s(), vertical = 10.dp.s()),
                        )

                        when (page) {
                            WELCOME -> {
                                Body(R.string.iori_first_welcome_body)
                                Spacer(Modifier.height(12.dp.s()))
                                Body(R.string.iori_first_privacy_body)
                                Spacer(Modifier.height(16.dp.s()))
                                Button(R.string.onboarding_read_policy) {
                                    activity?.let { ComposeDialogManager(context, it).showPrivacyPolicySheet() }
                                }
                            }

                            SETUP -> {
                                Body(R.string.iori_first_notifications_body)
                                Spacer(Modifier.height(8.dp.s()))
                                Button(
                                    if (hasAccess) R.string.onboarding_permission_granted
                                    else R.string.onboarding_grant_permission
                                ) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                Spacer(Modifier.height(16.dp.s()))
                                Body(R.string.iori_first_default_body)
                                Spacer(Modifier.height(8.dp.s()))
                                Button(
                                    if (isDefault) R.string.onboarding_default_set else R.string.onboarding_set_default
                                ) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                Spacer(Modifier.height(16.dp.s()))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // The pinch's two frames side by side, still: no animation on e-ink.
                                    for (frame in listOf(R.drawable.pinch1, R.drawable.pinch2)) {
                                        Image(
                                            painter = painterResource(frame),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(textColor),
                                            modifier = Modifier.size(32.dp.s()),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp.s()))
                                    Column(Modifier.weight(1f)) { Body(R.string.iori_first_settings_body) }
                                }
                            }

                            else -> {
                                Body(R.string.iori_first_theme_body)
                                Spacer(Modifier.height(8.dp.s()))
                                Button(R.string.onboarding_choose_preset) { showThemePicker = true }
                                Spacer(Modifier.height(24.dp.s()))
                                Body(R.string.iori_first_home_apps_body)
                                Spacer(Modifier.height(8.dp.s()))
                                Button(R.string.onboarding_edit_favorites, onEditFavorites)
                            }
                        }
                    }
                    if (page == WELCOME || page == SETUP) Tick(
                        text = stringResource(
                            if (page == WELCOME) R.string.onboarding_agree_policy else R.string.iori_first_settings_read
                        ),
                        checked = if (page == WELCOME) privacyAgreed else settingsRead,
                        fontSize = sizes.title,
                        textColor = textColor,
                        bgColor = bgColor,
                        shape = buttonShape,
                        modifier = Modifier.padding(horizontal = 20.dp.s()).padding(bottom = 8.dp.s()),
                    ) {
                        if (page == WELCOME) {
                            privacyAgreed = !privacyAgreed
                        } else {
                            settingsRead = !settingsRead
                        }
                    }
                }

                val blocked = (page == WELCOME && !privacyAgreed) || (page == SETUP && !settingsRead)
                Footer(
                    left = stringResource(R.string.btn_back_upper),
                    leftStruck = page == 0 && !rerun,
                    middle = "${page + 1}/$PAGES",
                    right = stringResource(
                        when {
                            page == LOOK && showThemePicker -> R.string.btn_select
                            page == LOOK -> R.string.btn_finish
                            else -> R.string.btn_next
                        }
                    ),
                    rightStruck = blocked,
                    // Said by TalkBack on every visit to NEXT, where the toast is shown only once.
                    rightWhy = when {
                        page == WELCOME && !privacyAgreed -> stringResource(R.string.iori_first_privacy_toast)
                        page == SETUP && !settingsRead -> stringResource(R.string.iori_first_settings_toast)
                        else -> null
                    },
                    fontSize = sizes.title,
                    textColor = textColor,
                    onLeft = { back() },
                    onRight = {
                        when {
                            page == WELCOME && !privacyAgreed -> {
                                if (!privacyToastShown) {
                                    Toast.makeText(context, R.string.iori_first_privacy_toast, Toast.LENGTH_LONG).show()
                                    privacyToastShown = true
                                }
                            }
                            page == SETUP && !settingsRead -> {
                                if (!settingsToastShown) {
                                    Toast.makeText(context, R.string.iori_first_settings_toast, Toast.LENGTH_LONG).show()
                                    settingsToastShown = true
                                }
                            }
                            page == LOOK && showThemePicker -> {
                                themeSelectAction.value?.invoke()
                                showThemePicker = false
                            }
                            page < PAGES - 1 -> page++
                            else -> onFinish()
                        }
                    },
                )
            }
        }
    }

    /** A tick box and its words, at the foot of a page that asks for one before NEXT. */
    @Composable
    private fun Tick(
        text: String,
        checked: Boolean,
        fontSize: TextUnit,
        textColor: Color,
        bgColor: Color,
        shape: Shape,
        modifier: Modifier = Modifier,
        onToggle: () -> Unit,
    ) {
        val screenScale = rememberScreenScale()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp.scaled(screenScale)),
            // A tick box to TalkBack too: its role and whether it is ticked.
            modifier = modifier
                .clip(shape)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = 12.dp.scaled(screenScale), vertical = 8.dp.scaled(screenScale)),
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = textColor,
                        uncheckedColor = textColor,
                        checkmarkColor = bgColor,
                    ),
                    modifier = Modifier.size(18.dp.scaled(screenScale)),
                )
            }
            Text(
                text = text,
                style = SettingsTheme.typography.title,
                fontSize = (fontSize.value * 0.7f).sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                letterSpacing = 2.sp,
            )
        }
    }

    /** The bar at the foot of every first-run page: left button, a count, right button. */
    @Composable
    internal fun ColumnScope.Footer(
        left: String,
        middle: String,
        right: String,
        fontSize: TextUnit,
        textColor: Color,
        leftStruck: Boolean = false,
        rightStruck: Boolean = false,
        rightWhy: String? = null,
        onLeft: () -> Unit,
        onRight: () -> Unit,
    ) {
        val screenScale = rememberScreenScale()
        val line = 1.5.dp.scaled(screenScale)
        Box(Modifier.fillMaxWidth().height(line).background(textColor))
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp.scaled(screenScale)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterButton(left, fontSize, textColor, leftStruck, Modifier.weight(1f), onLeft)
            Box(Modifier.width(line).fillMaxHeight().background(textColor))
            Box(Modifier.width(54.dp.scaled(screenScale)), contentAlignment = Alignment.Center) {
                Text(
                    text = middle,
                    style = SettingsTheme.typography.body,
                    fontSize = (fontSize.value * 0.7f).sp,
                    color = textColor,
                )
            }
            Box(Modifier.width(line).fillMaxHeight().background(textColor))
            FooterButton(right, fontSize, textColor, rightStruck, Modifier.weight(1f), onRight, rightWhy)
        }
        // A closing line when Android's own navigation bar is shown under the page.
        if (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() > 0.dp) {
            Box(Modifier.fillMaxWidth().height(line).background(textColor))
        }
    }

    @Composable
    private fun FooterButton(
        text: String,
        fontSize: TextUnit,
        color: Color,
        struck: Boolean,
        modifier: Modifier,
        onClick: () -> Unit,
        why: String? = null,
    ) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .clickable(onClick = onClick)
                // A struck button is not only a line through the word: TalkBack says why.
                .then(if (struck && why != null) Modifier.semantics { stateDescription = why } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = SettingsTheme.typography.title,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = color,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            )
        }
    }
}
