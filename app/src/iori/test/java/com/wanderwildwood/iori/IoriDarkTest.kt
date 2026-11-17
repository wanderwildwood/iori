package com.wanderwildwood.iori

import org.junit.Assert.assertEquals
import org.junit.Test

class IoriDarkTest {

    @Test
    fun whereTheSystemHasNoDarkSettingTheProviderSaysHomesSwitch() {
        assertEquals(1, IoriDark.answer(switchOn = true, systemHasSetting = false))
        assertEquals(0, IoriDark.answer(switchOn = false, systemHasSetting = false))
    }

    @Test
    fun whereTheSystemHasOneTheProviderNeverSaysDark() {
        // A switch left on from before would otherwise hold every app dark.
        assertEquals(0, IoriDark.answer(switchOn = true, systemHasSetting = true))
        assertEquals(0, IoriDark.answer(switchOn = false, systemHasSetting = true))
    }

    @Test
    fun theSwitchIsOnlyOnMuditaOs() {
        // MuditaOS has Android's dark screen installed but out of reach: Home's switch stays.
        assertEquals(false, IoriDark.hasDarkSetting(31, muditaOs = true))
        // Any other Android 10 or newer, stock Android 12 and KompaktOS (Android 16) among them.
        assertEquals(true, IoriDark.hasDarkSetting(31, muditaOs = false))
        assertEquals(true, IoriDark.hasDarkSetting(36, muditaOs = false))
        // Before Android 10 there is no system dark setting at all.
        assertEquals(false, IoriDark.hasDarkSetting(28, muditaOs = false))
    }

    @Test
    fun muditaOsIsMuditasSettingsOnASystemThatIsNotLineage() {
        assertEquals(true, IoriDark.muditaOs(muditaSettings = true, lineage = false))
        // KompaktOS is LineageOS on the same phone.
        assertEquals(false, IoriDark.muditaOs(muditaSettings = true, lineage = true))
        assertEquals(false, IoriDark.muditaOs(muditaSettings = false, lineage = true))
        assertEquals(false, IoriDark.muditaOs(muditaSettings = false, lineage = false))
    }
}
