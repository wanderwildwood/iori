package com.wanderwildwood.iori

import androidx.compose.runtime.Composable

/**
 * Your apps, opened from Settings. A stand-in until the installer page (branch `installer`)
 * takes this file's place: for now it is the Ecosystem apps list as it was.
 */
object IoriYourApps {
    @Composable
    fun Dialog(onClose: () -> Unit) {
        AppsDialog(onDismiss = onClose)
    }
}
