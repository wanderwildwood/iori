plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("com.mikepenz.aboutlibraries.plugin") version "12.2.4" apply false // iori: the Licenses list is made by the build (12.x: 15.x needs AGP 8.13)
}