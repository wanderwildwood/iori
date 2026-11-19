package com.wanderwildwood.iori.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * The house type scale for Home's own dialogs (Your apps, Home lines, About, the offer to
 * get an app): the sizes the other apps of this shop take from MMD, 28 / 24 / 20 / 18 / 16, with
 * nothing under 16 sp (the house floor; a 14 sp line is 22 px on the Kompakt, under the 24 px
 * its maker asks for). Home does not carry MMD, so the scale is written out here rather than
 * raised from MMD's, and the family is left to the theme. inkOS's own screens keep inkOS's
 * sizes; this is handed only to the dialogs Home adds:
 * `MaterialTheme(colorScheme = monochrome, typography = houseTypography)`.
 */

/** The smallest size the house scale sets, in sp. */
const val HOUSE_TYPE_FLOOR_SP: Float = 16f

private fun step(size: Int) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Medium)

val houseTypography: Typography = Typography().let { base ->
    base.copy(
        headlineLarge = step(28),
        titleLarge = step(24),
        titleMedium = step(20),
        bodyLarge = step(20),
        bodyMedium = step(18),
        labelLarge = step(18),
        titleSmall = step(16),
        bodySmall = step(16),
        labelMedium = step(16),
        labelSmall = step(16),
    )
}
