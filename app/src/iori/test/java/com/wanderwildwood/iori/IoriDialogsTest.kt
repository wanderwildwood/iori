package com.wanderwildwood.iori

import org.junit.Assert.assertEquals
import org.junit.Test

class IoriDialogsTest {

    @Test
    fun titleCaseBecomesSentenceCase() {
        assertEquals("Wallpaper visibility", IoriDialogs.sentenceCase("Wallpaper Visibility"))
        assertEquals("Quote size", IoriDialogs.sentenceCase("Quote Size"))
    }

    @Test
    fun wordsInCapitalsStay() {
        assertEquals("UI scale", IoriDialogs.sentenceCase("UI Scale"))
    }

    @Test
    fun sentenceCaseAndSingleWordsAreLeftAlone() {
        assertEquals("Clock text size", IoriDialogs.sentenceCase("Clock text size"))
        assertEquals("Nextcloud", IoriDialogs.sentenceCase("Nextcloud"))
        assertEquals("Open Home settings", IoriDialogs.sentenceCase("Open Home settings"))
    }

    @Test
    fun stepsStayInRange() {
        assertEquals(26, IoriDialogs.stepped(25, 1, 1, 25, 1500))
        assertEquals(25, IoriDialogs.stepped(25, -1, 1, 25, 1500))
        assertEquals(1500, IoriDialogs.stepped(1495, 10, 1, 25, 1500))
        assertEquals(50, IoriDialogs.stepped(25, 1, 25, 0, 500))
        assertEquals(-12, IoriDialogs.stepped(-11, -10, 1, -12, 14))
    }
}
