package com.wanderwildwood.iori.ui

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.github.gezimos.inkos.data.Constants
import com.github.gezimos.inkos.data.Prefs

/**
 * Home's own dialogs (Your apps, Home lines, About) are drawn black on white, as in the other
 * apps of this shop. While Home is in its dark theme their window is drawn through a filter
 * that swaps the two, so they are white on black like the screen behind them.
 *
 * The other apps have a `DarkWindow()` of the same name in the shared DarkMode.kt, which also
 * turns their whole screens. Home's screens are inkOS's and have a dark theme of their own,
 * so only this much is needed here, and EInkDialog calls it by the same name as theirs does.
 */
@Composable
fun DarkWindow() {
    val view = LocalView.current
    val context = LocalContext.current
    val theme = remember { Prefs(context).appTheme }
    val dark = when (theme) {
        Constants.Theme.Dark -> true
        Constants.Theme.Light -> false
        Constants.Theme.System -> isSystemInDarkTheme()
    }
    SideEffect {
        val root = view.rootView
        if (dark) root.setLayerType(View.LAYER_TYPE_HARDWARE, inverting) else root.setLayerType(View.LAYER_TYPE_NONE, null)
    }
}

private val inverting = Paint().apply {
    colorFilter = ColorMatrixColorFilter(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}
