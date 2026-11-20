package com.wanderwildwood.iori

import org.junit.Assert.assertEquals
import org.junit.Test

class IoriNamesTest {

    @Test
    fun aKnownSourceHasItsShownNameInstalledOrNot() {
        assertEquals("Medicine", IoriNames.shown("com.wanderwildwood.fukuyaku", null))
        assertEquals("Wallet", IoriNames.shown("com.wanderwildwood.satsuire", "Wallet"))
        assertEquals("Field Kit", IoriNames.shown("com.wanderwildwood.zatsuno", null))
        assertEquals("Datebook", IoriNames.shown("com.wanderwildwood.koyomi.debug", null))
        assertEquals("Audio Reader", IoriNames.shown("com.wanderwildwood.mimidoku", null))
        assertEquals("Memo", IoriNames.shown("com.wanderwildwood.oboegaki", null))
        assertEquals("Mailbox", IoriNames.shown("com.wanderwildwood.tayori", null))
        assertEquals("Time", IoriNames.shown("com.wanderwildwood.tokei", null))
    }

    @Test
    fun musicBoxAndMusicBoxKAreWatchedTogether() {
        assertEquals(listOf("com.wanderwildwood.jimeikin", "com.wanderwildwood.jimeikin.k"), IoriNames.providers("com.wanderwildwood.jimeikin"))
        assertEquals(listOf("com.wanderwildwood.jimeikin", "com.wanderwildwood.jimeikin.k"), IoriNames.providers("com.wanderwildwood.jimeikin.k"))
        assertEquals(listOf("com.wanderwildwood.koyomi"), IoriNames.providers("com.wanderwildwood.koyomi"))
        assertEquals("Music Box", IoriNames.shown("com.wanderwildwood.jimeikin", null))
    }

    @Test
    fun anUnknownSourceFallsBackToItsLabelThenItsPackage() {
        assertEquals("Music", IoriNames.shown("org.example.player", "Music"))
        assertEquals("org.example.player", IoriNames.shown("org.example.player", null))
        assertEquals("org.example.player", IoriNames.shown("org.example.player", " "))
    }

    @Test
    fun upstreamNameBecomesThisAppsName() {
        assertEquals(
            "To get the most out of Home, grant permissions so Home can forward",
            IoriNames.named("To get the most out of inkOS, grant permissions so inkOS can forward"),
        )
        assertEquals("What is Home Wallpaper?", IoriNames.named("What is inkOS Wallpaper?"))
    }

    @Test
    fun theFirstRunPrivacyPageSaysWhatHomeConnectsTo() {
        assertEquals(
            "Home does not collect, store, or share any data. There is no tracking. Home connects only to wanderthe.dev, for the list of apps when you open Your apps or get an app, to GitHub, to download the apps you get, and to your own Nextcloud if you sign in to it.",
            IoriNames.named("inkOS does not collect, store, or share any data. There are no server connections and no tracking. Everything stays on your device."),
        )
    }

    @Test
    fun linksAndItsOwnNameAreLeftAlone() {
        assertEquals("Home", IoriNames.named("Home"))
        assertEquals("github.com/gezimos/inkOS/issues", IoriNames.named("github.com/gezimos/inkOS/issues"))
        assertEquals("Reddit — r/inkos", IoriNames.named("Reddit — r/inkos"))
        assertEquals("Android", IoriNames.named("Android"))
    }
}
